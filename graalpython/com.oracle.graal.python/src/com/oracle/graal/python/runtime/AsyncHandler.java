/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.runtime;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * A handler for asynchronous actions events that need to be handled on a main thread of execution,
 * including signals and finalization.
 */
public class AsyncHandler {
    /**
     * An action to be run triggered by an asynchronous event.
     */
    public interface AsyncAction {
        void execute(PythonContext context);
    }

    public abstract static class AsyncPythonAction implements AsyncAction {
        /**
         * The object to call via a standard Python call
         */
        protected abstract Object callable();

        /**
         * The arguments to pass to the call
         */
        protected abstract Object[] arguments();

        /**
         * If the arguments need to include an element for the currently executing frame upon which
         * this async action is triggered, this method should return something >= 0. The array
         * returned by {@link #arguments()} should have a space for the frame already, as it will be
         * filled in without growing the arguments array.
         */
        protected int frameIndex() {
            return -1;
        }

        @Override
        public final void execute(PythonContext context) {
            Object callable = callable();
            if (callable != null) {
                Object[] arguments = arguments();
                Object[] args = PArguments.create(arguments.length + CallRootNode.ASYNC_ARG_COUNT);
                PythonUtils.arraycopy(arguments, 0, args, PArguments.USER_ARGUMENTS_OFFSET + CallRootNode.ASYNC_ARG_COUNT, arguments.length);
                PArguments.setArgument(args, CallRootNode.ASYNC_CALLABLE_INDEX, callable);
                PArguments.setArgument(args, CallRootNode.ASYNC_FRAME_INDEX_INDEX, frameIndex());

                try {
                    GenericInvokeNode.getUncached().execute(context.getAsyncHandler().callTarget, args);
                } catch (RuntimeException e) {
                    // we cannot raise the exception here (well, we could, but CPython
                    // doesn't), so we do what they do and just print it

                    // Just print a Python-like stack trace; CPython does the same (see
                    // 'weakrefobject.c: handle_callback')
                    ExceptionUtils.printPythonLikeStackTrace(e);
                }
            }
        }
    }

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(6, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    });

    private static final byte HAS_SCHEDULED_ACTION = 1;
    private static final byte SHOULD_RELEASE_GIL = 2;

    private volatile byte scheduledActionsFlags = 0;

    /**
     * We separate checking and running async actions in the compilation root from running the at
     * backedges of loops. At a compilation root it is quite cheap to do the check and branch into a
     * call, but at the backedges of loops this is expensive. So instead, we use the scheduled
     * actions to set this flag if we didn't enter an async action trigger in a reasonable time, and
     * the backedge triggers use a profile so that it is quite unlikely that short running loops or
     * loops with proper non-inlined calls in them would ever trigger async actions, since the
     * compilation roots are entered often enough.
     */
    private volatile boolean needsAdditionalSafepointExecution = false;

    private final WeakReference<PythonContext> context;
    private final ConcurrentLinkedQueue<AsyncAction> scheduledActions = new ConcurrentLinkedQueue<>();
    private ThreadLocal<Boolean> recursionGuard = new ThreadLocal<>();
    private static final int ASYNC_ACTION_DELAY = 15; // chosen by a fair D20 dice roll
    private static final int GIL_RELEASE_DELAY = 10;

    private class AsyncRunnable implements Runnable {
        private final Supplier<AsyncAction> actionSupplier;

        public AsyncRunnable(Supplier<AsyncAction> actionSupplier) {
            this.actionSupplier = actionSupplier;
        }

        @Override
        public void run() {
            AsyncAction asyncAction = actionSupplier.get();
            if (asyncAction != null) {
                scheduledActions.add(asyncAction);
                scheduledActionsFlags |= HAS_SCHEDULED_ACTION;
            }
        }
    }

    private static class CallRootNode extends PRootNode {
        static final int ASYNC_CALLABLE_INDEX = 0;
        static final int ASYNC_FRAME_INDEX_INDEX = 1;
        static final int ASYNC_ARG_COUNT = 2;

        @Child private CallNode callNode = CallNode.create();
        @Child private ReadCallerFrameNode readCallerFrameNode = ReadCallerFrameNode.create();
        @Child private CalleeContext calleeContext = CalleeContext.create();

        protected CallRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            Object[] frameArguments = frame.getArguments();
            Object callable = PArguments.getArgument(frameArguments, ASYNC_CALLABLE_INDEX);
            int frameIndex = (int) PArguments.getArgument(frameArguments, ASYNC_FRAME_INDEX_INDEX);
            Object[] arguments = Arrays.copyOfRange(frameArguments, PArguments.USER_ARGUMENTS_OFFSET + ASYNC_ARG_COUNT, frameArguments.length);

            if (frameIndex >= 0) {
                arguments[frameIndex] = readCallerFrameNode.executeWith(frame, 0);
            }
            try {
                return callNode.execute(frame, callable, arguments);
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return Signature.EMPTY;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean isInternal() {
            return true;
        }
    }

    private final RootCallTarget callTarget;

    AsyncHandler(PythonContext context) {
        this.context = new WeakReference<>(context);
        this.callTarget = context.getLanguage().createCachedCallTarget(l -> new CallRootNode(l), CallRootNode.class);
    }

    void registerAction(Supplier<AsyncAction> actionSupplier) {
        CompilerAsserts.neverPartOfCompilation();
        if (PythonLanguage.getContext().getOption(PythonOptions.NoAsyncActions)) {
            return;
        }
        executorService.scheduleWithFixedDelay(new AsyncRunnable(actionSupplier), ASYNC_ACTION_DELAY, ASYNC_ACTION_DELAY, TimeUnit.MILLISECONDS);
    }

    void activateGIL() {
        CompilerAsserts.neverPartOfCompilation();
        executorService.scheduleWithFixedDelay(() -> {
            if ((scheduledActionsFlags & SHOULD_RELEASE_GIL) != 0) {
                // didn't release the gil at all in the last GIL_RELEASE_DELAY timeframe. Panic.
                needsAdditionalSafepointExecution = true;
            }
            scheduledActionsFlags |= SHOULD_RELEASE_GIL;
        }, GIL_RELEASE_DELAY, GIL_RELEASE_DELAY, TimeUnit.MILLISECONDS);
    }

    void triggerAsyncActions() {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, scheduledActionsFlags != 0)) {
            triggerAsyncActionsBoundary();
        }
    }

    void triggerAsyncActionsProfiled(ConditionProfile profile) {
        if (profile.profile(needsAdditionalSafepointExecution)) {
            needsAdditionalSafepointExecution = false;
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, scheduledActionsFlags != 0)) {
                triggerAsyncActionsBoundary();
            }
        }
    }

    @TruffleBoundary
    private void triggerAsyncActionsBoundary() {
        if ((scheduledActionsFlags & SHOULD_RELEASE_GIL) != 0) {
            scheduledActionsFlags &= ~SHOULD_RELEASE_GIL;
            doReleaseGIL();
        }
        if ((scheduledActionsFlags & HAS_SCHEDULED_ACTION) != 0) {
            scheduledActionsFlags &= ~HAS_SCHEDULED_ACTION;
            processAsyncActions();
        }
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    private final void doReleaseGIL() {
        PythonContext ctx = context.get();
        if (ctx == null) {
            return;
        }
        try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
            Thread.yield();
        }
    }

    /**
     * We have a GIL, so when we enter this method, we own the GIL. Some async actions may cause us
     * to relinquish the GIL, and then other threads may come and start processing async actions.
     * That is fine, this processing can go on in parallel. E.g., Thread-1 may processes a few
     * weakref callbacks, then process a GIL release action. Thread-2 will still see the
     * hasScheduledAction flag be true when it next enters this method (in fact, Thread-2 may be
     * sitting in this method because it was processing an earlier GIL release action, but it
     * doesn't matter). Thread-2 will continue to process actions. If it's done, it will acquire the
     * action lock and reset the flag if the async action queue is empty (this way we don't race
     * between setting the flag and checking that the queue was empty). Thread-2 returns and
     * continues running until it once again relinquishes the GIL. Thread-1 may now wake up in this
     * method after getting the GIL back, but may not get any more actions from the queue, so it
     * leaves and continues running.
     *
     * We use a recursion guard to ensure that we don't recursively process during processing.
     */
    @TruffleBoundary
    private void processAsyncActions() {
        PythonContext ctx = context.get();
        if (ctx == null) {
            return;
        }
        if (recursionGuard.get() == Boolean.TRUE) {
            return;
        }
        recursionGuard.set(true);
        try {
            ConcurrentLinkedQueue<AsyncAction> actions = scheduledActions;
            AsyncAction action;
            while ((action = actions.poll()) != null) {
                action.execute(ctx);
            }
        } finally {
            recursionGuard.set(false);
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    public static class SharedFinalizer {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(SharedFinalizer.class);

        private final PythonContext pythonContext;
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

        /**
         * This is a Set of references to keep them alive after their gc collected referents.
         */
        private final ConcurrentMap<FinalizableReference, FinalizableReference> liveReferencesSet = new ConcurrentHashMap<>();

        public SharedFinalizer(PythonContext context) {
            this.pythonContext = context;
        }

        /**
         * Finalizable references is a utility class for freeing resources that {@link Runtime#gc()}
         * is unaware of, such as of heap allocation through native interface. Resources that can be
         * freed with {@link Runtime#gc()} should not extend this class.
         */
        public abstract static class FinalizableReference extends PhantomReference<Object> {
            private final Object reference;
            private boolean released;

            public FinalizableReference(Object referent, Object reference, SharedFinalizer sharedFinalizer) {
                super(referent, sharedFinalizer.queue);
                assert reference != null;
                this.reference = reference;
                addLiveReference(sharedFinalizer, this);
            }

            /**
             * We'll keep a reference for the FinalizableReference object until the async handler
             * schedule the collect process.
             */
            @TruffleBoundary
            private static void addLiveReference(SharedFinalizer sharedFinalizer, FinalizableReference ref) {
                sharedFinalizer.liveReferencesSet.put(ref, ref);
            }

            /**
             *
             * @return the undelying reference which is usually a native pointer.
             */
            public final Object getReference() {
                return reference;
            }

            public final boolean isReleased() {
                return released;
            }

            /**
             * Mark the FinalizableReference as freed in case it has been freed elsewhare. This will
             * avoid double-freeing the reference.
             */
            public final void markReleased() {
                this.released = true;
            }

            /**
             * This implements the proper way to free the allocated resources associated with the
             * reference.
             */
            public abstract AsyncHandler.AsyncAction release();
        }

        static class SharedFinalizerErrorCallback implements AsyncHandler.AsyncAction {

            private final Exception exception;
            private final FinalizableReference referece; // problematic reference

            SharedFinalizerErrorCallback(FinalizableReference referece, Exception e) {
                this.exception = e;
                this.referece = referece;
            }

            @Override
            public void execute(PythonContext context) {
                LOGGER.severe(String.format("Error during async action for %s caused by %s", referece.getClass().getSimpleName(), exception.getMessage()));
            }
        }

        /**
         * We register the Async action once on the first encounter of a creation of
         * {@link FinalizableReference}. This will reduce unnecessary Async thread load when there
         * isn't any enqueued references.
         */
        public void registerAsyncAction() {
            pythonContext.registerAsyncAction(() -> {
                Reference<? extends Object> reference = null;
                try {
                    reference = queue.remove();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (reference instanceof FinalizableReference) {
                    FinalizableReference object = (FinalizableReference) reference;
                    try {
                        liveReferencesSet.remove(object);
                        if (object.isReleased()) {
                            return null;
                        }
                        return object.release();
                    } catch (Exception e) {
                        return new SharedFinalizerErrorCallback(object, e);
                    }
                }
                return null;
            });

        }
    }
}
