/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(value = PythonObjectLibrary.class, receiverType = Double.class)
final class DefaultPythonDoubleExports {
    @ExportMessage
    static boolean isHashable(@SuppressWarnings("unused") Double value) {
        return true;
    }

    @ExportMessage
    static Object getLazyPythonClass(@SuppressWarnings("unused") Double value) {
        return PythonBuiltinClassType.PFloat;
    }

    @ExportMessage
    static long hash(Double number) {
        return hash(number.doubleValue());
    }

    @Ignore
    static long hash(double number) {
        if (number % 1 == 0) {
            return (long) number;
        } else {
            return Double.doubleToLongBits(number);
        }
    }

    @ExportMessage
    static boolean isTrue(Double value) {
        return value != 0.0;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean dd(Double receiver, double other) {
            return Double.compare(receiver, other) == 0;
        }

        @Specialization
        static boolean dF(Double receiver, PFloat other,
                        @Cached.Exclusive @Cached IsBuiltinClassProfile isFloat) {
            if (isFloat.profileObject(other, PythonBuiltinClassType.PFloat)) {
                return dd(receiver, other.getValue());
            } else {
                return false;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean dO(Double receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    static class EqualsInternal {
        @Specialization
        static int db(Double receiver, boolean other, @SuppressWarnings("unused") ThreadState threadState) {
            return (receiver == 1 && other || receiver == 0 && !other) ? 1 : 0;
        }

        @Specialization
        static int di(Double receiver, int other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int dl(Double receiver, long other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int dI(Double receiver, PInt other, @SuppressWarnings("unused") ThreadState threadState) {
            if (receiver % 1 == 0) {
                return other.compareTo(receiver.longValue()) == 0 ? 1 : 0;
            } else {
                return 0;
            }
        }

        @Specialization
        static int dd(Double receiver, double other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other ? 1 : 0;
        }

        @Specialization
        static int dF(Double receiver, PFloat other, @SuppressWarnings("unused") ThreadState threadState) {
            return receiver == other.getValue() ? 1 : 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        static int dO(Double receiver, Object other, @SuppressWarnings("unused") ThreadState threadState) {
            return -1;
        }
    }

    @ExportMessage
    static Object asPString(Double receiver,
                    @CachedLibrary("receiver") PythonObjectLibrary lib,
                    @CachedLibrary(limit = "1") PythonObjectLibrary resultLib,
                    @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                    @Exclusive @Cached PRaiseNode raise) {
        return PythonAbstractObject.asPString(lib, receiver, null, isSubtypeNode, resultLib, raise);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    static boolean canBeJavaDouble(@SuppressWarnings("unused") Double receiver) {
        return true;
    }

    @ExportMessage
    static double asJavaDouble(Double receiver) {
        return receiver;
    }

    @ExportMessage
    static boolean canBeJavaLong(@SuppressWarnings("unused") Double receiver) {
        return false;
    }

    @ExportMessage
    static long asJavaLong(Double receiver,
                    @Exclusive @Cached PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.MUST_BE_NUMERIC, receiver);
    }

    @ExportMessage
    static boolean canBePInt(@SuppressWarnings("unused") Double receiver) {
        return false;
    }

    @ExportMessage
    static int asPInt(Double receiver,
                    @Exclusive @Cached PRaiseNode raise) {
        throw raise.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, receiver);
    }

    @ExportMessage
    public static Object lookupAttributeInternal(Double receiver, ThreadState state, String name, boolean strict,
                    @Cached ConditionProfile gotState,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeNode lookup) {
        VirtualFrame frame = null;
        if (gotState.profile(state != null)) {
            frame = PArguments.frameForCall(state);
        }
        return lookup.execute(frame, receiver, name, strict);
    }

    @ExportMessage
    public static Object lookupAttributeOnTypeInternal(Double receiver, String name, boolean strict,
                    @CachedLibrary("receiver") PythonObjectLibrary lib,
                    @Exclusive @Cached PythonAbstractObject.LookupAttributeOnTypeNode lookup) {
        return lookup.execute(lib.getLazyPythonClass(receiver), name, strict);
    }

    @ExportMessage
    public static Object lookupAndCallSpecialMethodWithState(Double receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeOnTypeStrict(receiver, methodName);
        return methodLib.callUnboundMethodWithState(method, state, receiver, arguments);
    }

    @ExportMessage
    public static Object lookupAndCallRegularMethodWithState(Double receiver, ThreadState state, String methodName, Object[] arguments,
                    @CachedLibrary("receiver") PythonObjectLibrary plib,
                    @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib) {
        Object method = plib.lookupAttributeStrictWithState(receiver, state, methodName);
        return methodLib.callObjectWithState(method, state, arguments);
    }
}
