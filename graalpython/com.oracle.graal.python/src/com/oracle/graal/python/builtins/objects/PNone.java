/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// skip GIL
package com.oracle.graal.python.builtins.objects;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonObjectLibrary.class)
public final class PNone extends PythonAbstractObject {

    /**
     * Represents the {@code None} constant available to user code.
     */
    public static final PNone NONE = new PNone();

    /**
     * This is used when no value was provided for given argument, not even the {@code None} Python
     * level constant.
     */
    public static final PNone NO_VALUE = new PNone();

    private PNone() {
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return this == NONE ? "None" : "NoValue";
    }

    @Override
    public int compareTo(Object o) {
        return this.hashCode() - o.hashCode();
    }

    @ExportMessage
    static boolean isNull(@SuppressWarnings("unused") PNone self) {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isTrueWithState(@SuppressWarnings("unused") ThreadState state) {
        return false;
    }

    @ExportMessage
    Object getIteratorWithState(@SuppressWarnings("unused") ThreadState state,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, this);
    }
}
