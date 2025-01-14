/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils.TimeoutHelper;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * This class implements equivalents of OpenSSL transport functions ({@code SSL_read} etc) on top of
 * JDK's {@link SSLEngine}. It takes care of the handshake, packeting, network and application data
 * buffering and network or memory buffer IO. In addition to what the OpenSSL functions would do, it
 * also handles Python-specific error handling, non-blocking IO and socket timeouts.
 */
public class SSLEngineHelper {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int TLS_HEADER_SIZE = 5;

    /**
     * Equivalent of {@code SSL_write}. Attempts to transmit all the data in the supplied buffer
     * into given {@link PSSLSocket} (which can be backed by a memory buffer).
     *
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    @TruffleBoundary
    public static void write(PNodeWithRaise node, PSSLSocket socket, ByteBuffer input) {
        loop(node, socket, input, EMPTY_BUFFER, Operation.WRITE);
    }

    /**
     * Equivalent of {@code SSL_read}. Attempts to read bytes from the {@link PSSLSocket} (which can
     * be backed by a memory buffer) into given buffer. Will read at most the data of one TLS
     * packet. Decrypted but not read data is buffered on the socket and returned by the next call.
     * Empty output buffer after the call signifies the peer closed the connection cleanly.
     *
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    @TruffleBoundary
    public static void read(PNodeWithRaise node, PSSLSocket socket, ByteBuffer target) {
        loop(node, socket, EMPTY_BUFFER, target, Operation.READ);
    }

    /**
     * Equivalent of {@code SSL_do_handshake}. Initiate a handshake if one has not been initiated
     * already. Becomes a no-op after the initial handshake is done, i.e. cannot be used to perform
     * renegotiation.
     *
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    @TruffleBoundary
    public static void handshake(PNodeWithRaise node, PSSLSocket socket) {
        if (!socket.isHandshakeComplete()) {
            try {
                socket.getEngine().beginHandshake();
            } catch (SSLException e) {
                // TODO better error handling
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
            }
            loop(node, socket, EMPTY_BUFFER, EMPTY_BUFFER, Operation.HANDSHAKE);
        }
    }

    /**
     * Equivalent of {@code SSL_do_shutdown}. Initiates a duplex close of the TLS connection - sends
     * a close_notify message and tries to receive a close_notify from the peer.
     *
     * Errors are wrapped into Python exceptions. Requests for IO in non-blocking modes are
     * indicated using Python exceptions ({@code SSLErrorWantRead}, {@code SSLErrorWantWrite}).
     */
    @TruffleBoundary
    public static void shutdown(PNodeWithRaise node, PSSLSocket socket) {
        socket.getEngine().closeOutbound();
        loop(node, socket, EMPTY_BUFFER, EMPTY_BUFFER, Operation.SHUTDOWN);
    }

    private static void putAsMuchAsPossible(ByteBuffer target, PMemoryBIO sourceBIO) {
        ByteBuffer source = sourceBIO.getBufferForReading();
        int remaining = Math.min(source.remaining(), target.remaining());
        int oldLimit = source.limit();
        source.limit(source.position() + remaining);
        target.put(source);
        source.limit(oldLimit);
        sourceBIO.applyRead(source);
    }

    private enum Operation {
        READ,
        WRITE,
        HANDSHAKE,
        SHUTDOWN
    }

    private static void loop(PNodeWithRaise node, PSSLSocket socket, ByteBuffer appInput, ByteBuffer targetBuffer, Operation op) {
        PMemoryBIO applicationInboundBIO = socket.getApplicationInboundBIO();
        PMemoryBIO networkInboundBIO = socket.getNetworkInboundBIO();
        PMemoryBIO networkOutboundBIO = socket.getNetworkOutboundBIO();
        if (op == Operation.READ && applicationInboundBIO.getPending() > 0) {
            // Flush leftover data from previous read
            putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
            // OpenSSL's SSL_read returns only the pending data
            return;
        }
        SSLEngine engine = socket.getEngine();
        PSocket pSocket = socket.getSocket();
        TimeoutHelper timeoutHelper = null;
        if (pSocket != null) {
            long timeoutMillis = pSocket.getTimeoutInMilliseconds();
            if (timeoutMillis > 0) {
                timeoutHelper = new TimeoutHelper(timeoutMillis);
            }
        }
        // Whether we can write directly to targetBuffer
        boolean writeDirectlyToTarget = true;
        boolean currentlyWrapping;
        boolean didReadApplicationData = false;
        HandshakeStatus lastStatus;
        try {
            // Flush output that didn't get written in the last call (for non-blocking)
            emitOutputOrRaiseWantWrite(node, networkOutboundBIO, pSocket, timeoutHelper);
            transmissionLoop: while (true) {
                // If the handshake is not complete, do the operations that it requests
                // until it completes. This can happen in different situations:
                // * Initial handshake
                // * Renegotiation handshake, which can occur at any point in the communication
                // * Closing handshake. Can be initiated by us (#shutdown), the peer or when an
                // exception occurred
                lastStatus = engine.getHandshakeStatus();
                switch (lastStatus) {
                    case NEED_TASK:
                        socket.setHandshakeComplete(false);
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                        // Get the next step
                        continue transmissionLoop;
                    case NEED_WRAP:
                        socket.setHandshakeComplete(false);
                        currentlyWrapping = true;
                        break;
                    case NEED_UNWRAP:
                        socket.setHandshakeComplete(false);
                        currentlyWrapping = false;
                        break;
                    case NOT_HANDSHAKING:
                        currentlyWrapping = op != Operation.READ;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere("Unhandled SSL handshake status");
                }
                if (engine.isOutboundDone()) {
                    currentlyWrapping = false;
                }
                SSLEngineResult result;
                try {
                    if (currentlyWrapping) {
                        result = doWrap(engine, appInput, networkOutboundBIO, engine.getSession().getPacketBufferSize());
                    } else {
                        result = doUnwrap(engine, networkInboundBIO, targetBuffer, applicationInboundBIO, writeDirectlyToTarget);
                        didReadApplicationData = result.bytesProduced() > 0;
                    }
                } catch (SSLException e) {
                    // If a SSL exception occurs, we need to attempt to perform the closing
                    // handshake in order to let the peer know what went wrong. We raise the
                    // exception only after the handshake is done or if we get an exception the
                    // second time
                    if (socket.hasSavedException()) {
                        // We already got an exception in the previous iteration/call. Let's give up
                        // on the closing handshake to avoid going into an infinite loop
                        throw socket.getAndClearSavedException();
                    }
                    socket.setException(e);
                    engine.closeOutbound();
                    continue transmissionLoop;
                }
                if (result.getHandshakeStatus() == HandshakeStatus.FINISHED && !engine.isOutboundDone()) {
                    socket.setHandshakeComplete(true);
                }
                // Send the network output to socket, if any. If the output is a MemoryBIO, the
                // output is already in it at this point
                emitOutputOrRaiseWantWrite(node, networkOutboundBIO, pSocket, timeoutHelper);
                // Handle possible closure
                if (result.getStatus() == Status.CLOSED) {
                    engine.closeOutbound();
                }
                if (engine.isOutboundDone() && engine.isInboundDone()) {
                    // Closure handshake is done, we can handle the exit conditions now
                    if (socket.hasSavedException()) {
                        throw socket.getAndClearSavedException();
                    }
                    switch (op) {
                        case READ:
                            // Read operation should just return the current output. If it's
                            // empty, the application will interpret is as EOF, which is what is
                            // expected
                            break transmissionLoop;
                        case SHUTDOWN:
                            // Shutdown is considered done at this point
                            break transmissionLoop;
                        case WRITE:
                        case HANDSHAKE:
                            // Write and handshake operations need to fail loudly
                            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_ZERO_RETURN, ErrorMessages.SSL_SESSION_CLOSED);
                    }
                    throw CompilerDirectives.shouldNotReachHere();
                }
                // Try extra hard to converge on the status before doing potentially blocking
                // operations
                if (engine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING && engine.getHandshakeStatus() != lastStatus) {
                    continue transmissionLoop;
                }
                // Decide if we need to obtain more data or change the buffer
                switch (result.getStatus()) {
                    case BUFFER_OVERFLOW:
                        if (currentlyWrapping) {
                            throw CompilerDirectives.shouldNotReachHere("Unexpected overflow of network buffer");
                        }
                        // We are trying to read a packet whose content doesn't fit into the
                        // output buffer. That means we need to read the whole content into a
                        // temporary buffer, then copy as much as we can into the target buffer
                        // and save the rest for the next read call
                        writeDirectlyToTarget = false;
                        continue transmissionLoop;
                    case BUFFER_UNDERFLOW:
                        // We need to obtain more input from the socket or MemoryBIO
                        int readBytes = obtainMoreInput(node, engine, networkInboundBIO, pSocket, timeoutHelper);
                        if (readBytes == 0) {
                            // Non-blocking socket or BIO with not enough input
                            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_READ, ErrorMessages.SSL_WANT_READ);
                        } else if (readBytes < 0) {
                            // We got EOF
                            if (socket.hasSavedException()) {
                                throw socket.getAndClearSavedException();
                            }
                            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_EOF, ErrorMessages.SSL_ERROR_EOF);
                        }
                        continue transmissionLoop;
                }
                // Continue handshaking until done
                if (engine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
                    continue transmissionLoop;
                }
                switch (result.getStatus()) {
                    case OK:
                        // At this point, the handshake is complete and the session is not closed.
                        // Decide what to do about the actual application-level operation
                        switch (op) {
                            case READ:
                                // Read operation needs to return after a single packet of
                                // application data has been read
                                if (didReadApplicationData) {
                                    break transmissionLoop;
                                }
                                continue transmissionLoop;
                            case HANDSHAKE:
                                // Handshake is done at this point
                                break transmissionLoop;
                            case WRITE:
                                // Write operation needs to continue until the buffer is empty
                                if (appInput.hasRemaining()) {
                                    continue transmissionLoop;
                                }
                                break transmissionLoop;
                            case SHUTDOWN:
                                // Continue the closing handshake
                                continue transmissionLoop;
                        }
                        throw CompilerDirectives.shouldNotReachHere();
                    case CLOSED:
                        // SSLEngine says there's no handshake, but there could still be a response
                        // to our close waiting to be read
                        continue transmissionLoop;
                }
                throw CompilerDirectives.shouldNotReachHere("Unhandled SSL engine status");
            }
            // The loop exit - the operation finished (doesn't need more input)
            if (socket.hasSavedException()) {
                // We encountered an error during the communication and we temporarily suppressed it
                // to perform the closing handshake. Now we should process it
                throw socket.getAndClearSavedException();
            }
            assert !appInput.hasRemaining();
            // The operation finished successfully at this point
        } catch (SSLException e) {
            throw handleSSLException(node, e);
        } catch (IOException e) {
            // TODO better error handling, distinguish SSL errors and socket errors
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SYSCALL, e.toString());
        } catch (OverflowException | OutOfMemoryError e) {
            throw PRaiseNode.raiseUncached(node, MemoryError);
        }
        // TODO handle other socket errors (NotYetConnected)
    }

    private static SSLEngineResult doUnwrap(SSLEngine engine, PMemoryBIO networkInboundBIO, ByteBuffer targetBuffer, PMemoryBIO applicationInboundBIO, boolean writeDirectlyToTarget)
                    throws SSLException, OverflowException {
        ByteBuffer readBuffer = networkInboundBIO.getBufferForReading();
        try {
            if (writeDirectlyToTarget) {
                return engine.unwrap(readBuffer, targetBuffer);
            } else {
                applicationInboundBIO.ensureWriteCapacity(engine.getSession().getApplicationBufferSize());
                ByteBuffer writeBuffer = applicationInboundBIO.getBufferForWriting();
                try {
                    return engine.unwrap(readBuffer, writeBuffer);
                } finally {
                    applicationInboundBIO.applyWrite(writeBuffer);
                    putAsMuchAsPossible(targetBuffer, applicationInboundBIO);
                }
            }
        } finally {
            networkInboundBIO.applyRead(readBuffer);
        }
    }

    private static SSLEngineResult doWrap(SSLEngine engine, ByteBuffer appInput, PMemoryBIO networkOutboundBIO, int netBufferSize) throws SSLException, OverflowException {
        networkOutboundBIO.ensureWriteCapacity(netBufferSize);
        ByteBuffer writeBuffer = networkOutboundBIO.getBufferForWriting();
        try {
            return engine.wrap(appInput, writeBuffer);
        } finally {
            networkOutboundBIO.applyWrite(writeBuffer);
        }
    }

    private static PException handleSSLException(PNodeWithRaise node, SSLException e) {
        if (e.getCause() instanceof CertificateException) {
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_CERT_VERIFICATION, ErrorMessages.CERTIFICATE_VERIFY_FAILED, e.toString());
        }
        throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL, e.toString());
    }

    @SuppressWarnings("try")
    private static int obtainMoreInput(PNodeWithRaise node, SSLEngine engine, PMemoryBIO networkInboundBIO, PSocket socket, TimeoutHelper timeoutHelper) throws IOException, OverflowException {
        if (socket != null) {
            if (socket.getSocket() == null) {
                // TODO use raiseOsError with ENOTCONN
                throw node.raise(OSError);
            }
            // Network input
            // OpenSSL only reads as much as necessary for given packet. SSLEngine doesn't tell
            // us how much it expects. The size returned by getPacketBufferSize() is the maximum
            // expected size, not the actual size. CPython has some situations that rely on not
            // reading more than the packet, notably after the SSL connection is closed by a proper
            // closing handshake, the socket can be used for plaintext communication. If we
            // over-read, we would read that plaintext and it would get discarded. So we try to get
            // at least the 5 bytes for the header and then determine the packet size from the
            // header. If the packet is not SSL, the engine should reject it as soon as it gets the
            // header.
            int len;
            if (networkInboundBIO.getPending() >= TLS_HEADER_SIZE) {
                len = TLS_HEADER_SIZE + ((networkInboundBIO.getByte(3) & 0xFF) << 8) +
                                (networkInboundBIO.getByte(4) & 0xFF);
            } else {
                len = TLS_HEADER_SIZE;
            }
            if (networkInboundBIO.getPending() >= len) {
                // The engine requested more data, but we think we already got enough data.
                // Don't argue with it and give it what it wants. We give up on having no
                // read-ahead, but that's better than crashing
                len = engine.getSession().getPacketBufferSize();
            }
            int toRead = len - networkInboundBIO.getPending();
            networkInboundBIO.ensureWriteCapacity(toRead);
            ByteBuffer writeBuffer = networkInboundBIO.getBufferForWriting();
            // Avoid reading more that we determined
            writeBuffer.limit(writeBuffer.position() + toRead);
            try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
                return SocketUtils.recv(node, socket, writeBuffer, timeoutHelper == null ? 0 : timeoutHelper.checkAndGetRemainingTimeout(node));
            } finally {
                networkInboundBIO.applyWrite(writeBuffer);
            }
        } else if (networkInboundBIO.didWriteEOF()) {
            // MemoryBIO output with signalled EOF
            // Note this checks didWriteEOF and not isEOF - the fact that we're here means that we
            // consumed as much data as possible to form a TLS packet, but that doesn't have to be
            // all the data in the BIO
            return -1;
        } else {
            // MemoryBIO input, cannot read anything more than what's already there
            return 0;
        }
    }

    @SuppressWarnings("try")
    private static void emitOutputOrRaiseWantWrite(PNodeWithRaise node, PMemoryBIO networkOutboundBIO, PSocket socket, TimeoutHelper timeoutHelper) throws IOException {
        if (socket != null && networkOutboundBIO.getPending() > 0) {
            if (socket.getSocket() == null) {
                // TODO use raiseOsError with ENOTCONN
                throw node.raise(OSError);
            }
            int pendingBytes = networkOutboundBIO.getPending();
            // Network output
            ByteBuffer readBuffer = networkOutboundBIO.getBufferForReading();
            int writtenBytes;
            try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
                writtenBytes = SocketUtils.send(node, socket, readBuffer, timeoutHelper == null ? 0 : timeoutHelper.checkAndGetRemainingTimeout(node));
            } finally {
                networkOutboundBIO.applyRead(readBuffer);
            }
            if (writtenBytes < pendingBytes) {
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_WANT_WRITE, ErrorMessages.SSL_WANT_WRITE);
            }
        }
    }
}
