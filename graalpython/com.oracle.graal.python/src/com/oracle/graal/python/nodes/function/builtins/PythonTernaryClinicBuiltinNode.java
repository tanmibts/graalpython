/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function.builtins;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ClinicBuiltinBaseClass;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

@ClinicBuiltinBaseClass
public abstract class PythonTernaryClinicBuiltinNode extends PythonTernaryBuiltinNode {
    private @Child ArgumentCastNode castNode0;
    private @Child ArgumentCastNode castNode1;
    private @Child ArgumentCastNode castNode2;

    /**
     * Returns the provider of argument clinic logic. It should be singleton instance of a class
     * generated from the {@link ArgumentClinic} annotations.
     */
    protected abstract ArgumentClinicProvider getArgumentClinic();

    private Object cast0WithNode(ArgumentClinicProvider clinic, VirtualFrame frame, Object value) {
        if (castNode0 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castNode0 = insert(clinic.createCastNode(0, this));
        }
        return castNode0.execute(frame, value);
    }

    private Object cast1WithNode(ArgumentClinicProvider clinic, VirtualFrame frame, Object value) {
        if (castNode1 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castNode1 = insert(clinic.createCastNode(1, this));
        }
        return castNode1.execute(frame, value);
    }

    private Object cast2WithNode(ArgumentClinicProvider clinic, VirtualFrame frame, Object value) {
        if (castNode2 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castNode2 = insert(clinic.createCastNode(2, this));
        }
        return castNode2.execute(frame, value);
    }

    @Override
    public final Object callWithInt(VirtualFrame frame, Object arg, int arg2, Object arg3) {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsInt(1)) {
            Object val = clinic.hasCastNode(0) ? cast0WithNode(clinic, frame, arg) : arg;
            Object val3 = clinic.hasCastNode(2) ? cast2WithNode(clinic, frame, arg3) : arg3;
            return executeWithInt(frame, val, arg2, val3);
        } else {
            return call(frame, arg, arg2, arg3);
        }
    }

    @Override
    public final Object call(VirtualFrame frame, Object arg, Object arg2, Object arg3) {
        ArgumentClinicProvider clinic = getArgumentClinic();
        Object val = clinic.hasCastNode(0) ? cast0WithNode(clinic, frame, arg) : arg;
        Object val2 = clinic.hasCastNode(1) ? cast1WithNode(clinic, frame, arg2) : arg2;
        Object val3 = clinic.hasCastNode(2) ? cast2WithNode(clinic, frame, arg3) : arg3;
        return execute(frame, val, val2, val3);
    }
}
