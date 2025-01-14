/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

// TODO interop lib
@ExportLibrary(PythonObjectLibrary.class)
public final class PMemoryView extends PythonBuiltinObject {
    public static final int MAX_DIM = 64;

    public static final int FLAG_RELEASED = 0x001;
    public static final int FLAG_C = 0x002;
    public static final int FLAG_FORTRAN = 0x004;
    public static final int FLAG_SCALAR = 0x008;
    public static final int FLAG_PIL = 0x010;

    private Object owner;
    private final int len;
    private final boolean readonly;
    private final int itemsize;
    private final String formatString;
    private final BufferFormat format;
    private final int ndim;
    // We cannot easily add numbers to pointers in Java, so the actual pointer is bufPointer +
    // offset
    private final Object bufPointer;
    private final int offset;
    private final int[] shape;
    private final int[] strides;
    private final int[] suboffsets;

    // Count of exports via native buffer interface
    private final AtomicLong exports = new AtomicLong();
    // Phantom ref to this object that will decref/release the managed buffer if any
    private BufferReference reference;
    private int flags;

    // Cached hash value, required to compy with CPython's semantics
    private int cachedHash = -1;

    public PMemoryView(Object cls, Shape instanceShape, PythonContext context, ManagedBuffer managedBuffer, Object owner,
                    int len, boolean readonly, int itemsize, BufferFormat format, String formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        super(cls, instanceShape);
        this.owner = owner;
        this.len = len;
        this.readonly = readonly;
        this.itemsize = itemsize;
        this.format = format;
        this.formatString = formatString;
        this.ndim = ndim;
        this.bufPointer = bufPointer;
        this.offset = offset;
        this.shape = shape;
        this.strides = strides;
        this.suboffsets = suboffsets;
        this.flags = flags;
        if (managedBuffer != null) {
            this.reference = BufferReference.createBufferReference(this, managedBuffer, context);
        }
    }

    // From CPython init_strides_from_shape
    public static int[] initStridesFromShape(int ndim, int itemsize, int[] shape) {
        int[] strides = new int[ndim];
        strides[ndim - 1] = itemsize;
        for (int i = ndim - 2; i >= 0; i--) {
            strides[i] = strides[i + 1] * shape[i + 1];
        }
        return strides;
    }

    public ManagedBuffer getManagedBuffer() {
        return (reference != null) ? reference.getManagedBuffer() : null;
    }

    public Object getOwner() {
        return owner;
    }

    public int getLength() {
        return len;
    }

    public boolean isReadOnly() {
        return readonly;
    }

    public int getItemSize() {
        return itemsize;
    }

    public String getFormatString() {
        return formatString;
    }

    public BufferFormat getFormat() {
        return format;
    }

    public int getDimensions() {
        return ndim;
    }

    public Object getBufferPointer() {
        return bufPointer;
    }

    public int getOffset() {
        return offset;
    }

    public int[] getBufferShape() {
        return shape;
    }

    public int[] getBufferStrides() {
        return strides;
    }

    public int[] getBufferSuboffsets() {
        return suboffsets;
    }

    public boolean isReleased() {
        return (flags & FLAG_RELEASED) != 0;
    }

    public boolean isCContiguous() {
        return (flags & FLAG_C) != 0;
    }

    public boolean isFortranContiguous() {
        return (flags & FLAG_FORTRAN) != 0;
    }

    public int getFlags() {
        return flags;
    }

    public AtomicLong getExports() {
        return exports;
    }

    public BufferReference getReference() {
        return reference;
    }

    public int getCachedHash() {
        return cachedHash;
    }

    public void setCachedHash(int cachedHash) {
        this.cachedHash = cachedHash;
    }

    public void setReleased() {
        flags |= FLAG_RELEASED;
        if (reference != null) {
            reference.markReleased();
            reference = null;
        }
        owner = null;
    }

    public void checkReleased(PRaiseNode raiseNode) {
        if (isReleased()) {
            throw raiseNode.raise(ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
    }

    public void checkReleased(PNodeWithRaise node) {
        if (isReleased()) {
            throw node.raise(ValueError, ErrorMessages.MEMORYVIEW_FORBIDDEN_RELEASED);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return getLength();
    }

    @ExportMessage
    byte[] getBufferBytes(@Cached MemoryViewNodes.ToJavaBytesNode toJavaBytesNode) {
        return toJavaBytesNode.execute(this);
    }
}
