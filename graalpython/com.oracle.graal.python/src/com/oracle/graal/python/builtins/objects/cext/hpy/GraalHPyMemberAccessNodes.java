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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_BOOL;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_BYTE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_CHAR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_FLOAT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_HPYSSIZET;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_INT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_LONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_LONGLONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_NONE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_OBJECT_EX;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_SHORT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_STRING;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_STRING_INPLACE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_UBYTE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_UINT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_ULONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_ULONGLONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_USHORT;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins.GetRefNode;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsFixedNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeBooleanNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonBooleanNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativeUnsignedPrimitiveAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.StringAsPythonStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyBadMemberDescrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadOnlyMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyWriteMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyConvertArgsToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetGetterToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetSetSetterToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckPrimitiveResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

public class GraalHPyMemberAccessNodes {

    static GraalHPyNativeSymbol getReadAccessorName(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_S;
            case HPY_MEMBER_INT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_I;
            case HPY_MEMBER_LONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_L;
            case HPY_MEMBER_FLOAT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_F;
            case HPY_MEMBER_DOUBLE:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_D;
            case HPY_MEMBER_STRING:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_STRING;
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY;
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_C;
            case HPY_MEMBER_UBYTE:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_UC;
            case HPY_MEMBER_USHORT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_US;
            case HPY_MEMBER_UINT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI;
            case HPY_MEMBER_ULONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_UL;
            case HPY_MEMBER_STRING_INPLACE:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_STRING_IN_PLACE;
            case HPY_MEMBER_LONGLONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_LL;
            case HPY_MEMBER_ULONGLONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_ULL;
            case HPY_MEMBER_HPYSSIZET:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY_SSIZE_T;
            case HPY_MEMBER_NONE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtAsPythonObjectNode getReadConverterNode(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
            case HPY_MEMBER_INT:
            case HPY_MEMBER_LONG:
            case HPY_MEMBER_FLOAT:
            case HPY_MEMBER_DOUBLE:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_UBYTE:
            case HPY_MEMBER_USHORT:
            case HPY_MEMBER_STRING_INPLACE:
            case HPY_MEMBER_HPYSSIZET:
            case HPY_MEMBER_NONE:
                // no conversion needed
                return null;
            case HPY_MEMBER_STRING:
                return StringAsPythonStringNodeGen.create();
            case HPY_MEMBER_BOOL:
                return NativePrimitiveAsPythonBooleanNodeGen.create();
            case HPY_MEMBER_CHAR:
                return NativePrimitiveAsPythonCharNodeGen.create();
            case HPY_MEMBER_UINT:
            case HPY_MEMBER_ULONG:
            case HPY_MEMBER_LONGLONG:
            case HPY_MEMBER_ULONGLONG:
                return NativeUnsignedPrimitiveAsPythonObjectNodeGen.create();
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return HPyAsPythonObjectNodeGen.create();
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static GraalHPyNativeSymbol getWriteAccessorName(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_S;
            case HPY_MEMBER_INT:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I;
            case HPY_MEMBER_LONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_L;
            case HPY_MEMBER_FLOAT:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_F;
            case HPY_MEMBER_DOUBLE:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_D;
            case HPY_MEMBER_NONE:
            case HPY_MEMBER_STRING:
            case HPY_MEMBER_STRING_INPLACE:
                return null;
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY;
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_C;
            case HPY_MEMBER_UBYTE:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UC;
            case HPY_MEMBER_USHORT:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_US;
            case HPY_MEMBER_UINT:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI;
            case HPY_MEMBER_ULONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UL;
            case HPY_MEMBER_LONGLONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_LL;
            case HPY_MEMBER_ULONGLONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_ULL;
            case HPY_MEMBER_HPYSSIZET:
                return GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY_SSIZE_T;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtToNativeNode getWriteConverterNode(int type) {
        switch (type) {
            case HPY_MEMBER_CHAR:
                return AsNativeCharNodeGen.create();
            case HPY_MEMBER_BOOL:
                return AsNativeBooleanNodeGen.create();
            case HPY_MEMBER_SHORT:
            case HPY_MEMBER_INT:
            case HPY_MEMBER_BYTE:
                // TODO(fa): use appropriate native type sizes
                return AsFixedNativePrimitiveNodeGen.create(Integer.BYTES, true);
            case HPY_MEMBER_LONG:
            case HPY_MEMBER_HPYSSIZET:
                // TODO(fa): use appropriate native type sizes
                return AsFixedNativePrimitiveNodeGen.create(Long.BYTES, true);
            case HPY_MEMBER_FLOAT:
            case HPY_MEMBER_DOUBLE:
                return AsNativeDoubleNodeGen.create();
            case HPY_MEMBER_USHORT:
            case HPY_MEMBER_UINT:
            case HPY_MEMBER_UBYTE:
                // TODO(fa): use appropriate native type sizes
                return AsFixedNativePrimitiveNodeGen.create(Integer.BYTES, false);
            case HPY_MEMBER_ULONG:
            case HPY_MEMBER_LONGLONG:
            case HPY_MEMBER_ULONGLONG:
                // TODO(fa): use appropriate native type sizes
                return AsFixedNativePrimitiveNodeGen.create(Long.BYTES, false);
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
            case HPY_MEMBER_NONE:
            case HPY_MEMBER_STRING:
            case HPY_MEMBER_STRING_INPLACE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    /**
     * Special case: members with type {@code STRING} and {@code STRING_INPLACE} are always
     * read-only.
     */
    static boolean isReadOnlyType(int type) {
        return type == HPY_MEMBER_STRING || type == HPY_MEMBER_STRING_INPLACE;
    }

    public static class HPyMemberNodeFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;

        public HPyMemberNodeFactory(T node) {
            this.node = node;
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return determineNodeClass(node);
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> determineNodeClass(T node) {
            CompilerAsserts.neverPartOfCompilation();
            Class<T> nodeClass = (Class<T>) node.getClass();
            GeneratedBy genBy = nodeClass.getAnnotation(GeneratedBy.class);
            if (genBy != null) {
                nodeClass = (Class<T>) genBy.value();
                assert nodeClass.isAssignableFrom(node.getClass());
            }
            return nodeClass;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            throw new IllegalAccessError();
        }

    }

    @Builtin(name = "hpy_member_read", minNumOfPositionalArgs = 1, parameterNames = "$self")
    protected abstract static class HPyReadMemberNode extends PythonUnaryBuiltinNode {
        private static final Builtin builtin = HPyReadMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;
        @Child private PForeignToPTypeNode fromForeign;
        @Child private HPyGetNativeSpacePointerNode readNativeSpaceNode;

        /** The name of the native getter function. */
        private final GraalHPyNativeSymbol accessor;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyReadMemberNode(GraalHPyNativeSymbol accessor, int offset, CExtAsPythonObjectNode asPythonObjectNode) {
            this.accessor = accessor;
            this.offset = offset;
            this.asPythonObjectNode = asPythonObjectNode;
            if (asPythonObjectNode == null) {
                fromForeign = PForeignToPTypeNode.create();
            }
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().execute(self);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(PythonBuiltinClassType.SystemError, "Attempting to read from offset %d but object '%s' has no associated native space.", offset, self);
            }

            Object nativeResult = PNone.NONE;
            if (accessor != null) {
                // This will call pure C functions that won't ever access the Python stack nor the
                // exception state. So, we don't need to setup an indirect call.
                nativeResult = ensureCallHPyFunctionNode().call(hPyContext, accessor, nativeSpacePtr, (long) offset);
                if (asPythonObjectNode != null) {
                    return asPythonObjectNode.execute(hPyContext, nativeResult);
                }
            }
            // We still need to use 'PForeignToPTypeNode' to ensure that we do not introduce unknown
            // values into our value space.
            return fromForeign.executeConvert(nativeResult);
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private HPyGetNativeSpacePointerNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            return readNativeSpaceNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName, int type, int offset) {
            GraalHPyNativeSymbol accessor = getReadAccessorName(type);
            CExtAsPythonObjectNode asPythonObjectNode = getReadConverterNode(type);
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyReadMemberNodeGen.create(accessor, offset, asPythonObjectNode)), true),
                            HPyReadMemberNode.class, builtin.name(), type, offset);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, callTarget);
        }
    }

    @Builtin(name = "hpy_member_write_read_only", minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"})
    protected abstract static class HPyReadOnlyMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyReadOnlyMemberNode.class.getAnnotation(Builtin.class);

        @Specialization
        @SuppressWarnings("unused")
        static Object doGeneric(Object self, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.READONLY_ATTRIBUTE);
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName) {
            RootCallTarget builtinCt = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyReadOnlyMemberNodeGen.create()), true),
                            GraalHPyMemberAccessNodes.class, builtin.name());
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, builtinCt);
        }
    }

    @Builtin(name = "hpy_bad_member_descr", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    protected abstract static class HPyBadMemberDescrNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyBadMemberDescrNode.class.getAnnotation(Builtin.class);

        @Specialization
        Object doGeneric(Object self, @SuppressWarnings("unused") Object value) {
            if (value == DescriptorDeleteMarker.INSTANCE) {
                // This node is actually only used for T_NONE, so this error message is right.
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
            }
            throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.BAD_MEMBER_DESCR_TYPE_FOR_P, self);
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName) {
            RootCallTarget builtinCt = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyBadMemberDescrNodeGen.create()), true),
                            HPyBadMemberDescrNode.class, builtin);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, builtinCt);
        }
    }

    @Builtin(name = "hpy_write_member", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    protected abstract static class HPyWriteMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyWriteMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtToNativeNode toNativeNode;
        @Child private HPyGetNativeSpacePointerNode readNativeSpaceNode;
        @Child private InteropLibrary resultLib;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyWriteMemberNode(int type, int offset) {
            this.type = type;
            this.offset = offset;
            this.toNativeNode = getWriteConverterNode(type);
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object value) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().execute(self);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(PythonBuiltinClassType.SystemError, "Attempting to write to offset %d but object '%s' has no associated native space.", offset, self);
            }

            /*
             * Deleting values is only allowed for members with object type (see structmember.c:
             * PyMember_SetOne).
             */
            Object newValue;
            if (value == DescriptorDeleteMarker.INSTANCE) {
                if (type == HPY_MEMBER_OBJECT_EX) {
                    if (resultLib == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        resultLib = insert(InteropLibrary.getFactory().createDispatched(2));
                    }
                    /*
                     * This will call pure C functions that won't ever access the Python stack nor
                     * the exception state. So, we don't need to setup an indirect call.
                     */
                    Object oldValue = ensureCallHPyFunctionNode().call(hPyContext, getReadAccessorName(type), nativeSpacePtr, (long) offset);
                    if (resultLib.isNull(oldValue)) {
                        throw raise(PythonBuiltinClassType.AttributeError);
                    }
                } else if (type != HPY_MEMBER_OBJECT) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
                }
                // NO_VALUE will be converted to the NULL handle
                newValue = PNone.NO_VALUE;
            } else {
                newValue = value;
            }

            GraalHPyNativeSymbol accessor = getWriteAccessorName(type);
            if (accessor != null) {
                // convert value if needed
                Object nativeValue;
                if (toNativeNode != null) {
                    // The conversion to a native primitive may call arbitrary user code. So we need
                    // to prepare an indirect call.
                    Object savedState = IndirectCallContext.enter(frame, getContext(), this);
                    try {
                        nativeValue = toNativeNode.execute(hPyContext, newValue);
                    } finally {
                        IndirectCallContext.exit(frame, getContext(), savedState);
                    }
                } else {
                    nativeValue = newValue;
                }

                // This will call pure C functions that won't ever access the Python stack nor the
                // exception state. So, we don't need to setup an indirect call.
                ensureCallHPyFunctionNode().call(hPyContext, accessor, nativeSpacePtr, (long) offset, nativeValue);
            }
            return PNone.NONE;
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private HPyGetNativeSpacePointerNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            return readNativeSpaceNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName, int type, int offset) {
            GraalHPyNativeSymbol accessor = getWriteAccessorName(type);
            if (accessor == null) {
                if (isReadOnlyType(type)) {
                    return HPyReadOnlyMemberNode.createBuiltinFunction(language, propertyName);
                }
                return HPyBadMemberDescrNode.createBuiltinFunction(language, propertyName);
            }
            //
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyWriteMemberNodeGen.create(type, offset)), true),
                            HPyWriteMemberNode.class, builtin.name(), type, offset);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, callTarget);
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    abstract static class HPyGetSetDescriptorRootNode extends PRootNode {

        /**
         * The index of the cell that contains the target (i.e. the pointer of native getter/setter
         * function).
         */
        static final int CELL_INDEX_TARGET = 0;

        /**
         * The index of the cell that contains the native closure pointer (i.e. a native pointer of
         * type {@code void*}).
         */
        static final int CELL_INDEX_CLOSURE = 1;

        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private CellBuiltins.GetRefNode readTargetCellNode;
        @Child private CellBuiltins.GetRefNode readClosureCellNode;

        private final String name;

        HPyGetSetDescriptorRootNode(PythonLanguage language, String name) {
            super(language);
            this.name = name;
        }

        static PCell[] createPythonClosure(Object target, Object closure, PythonObjectFactory factory) {
            PCell[] pythonClosure = new PCell[2];

            PCell targetCell = factory.createCell(Truffle.getRuntime().createAssumption());
            targetCell.setRef(target);
            pythonClosure[CELL_INDEX_TARGET] = targetCell;

            PCell closureCell = factory.createCell(Truffle.getRuntime().createAssumption());
            closureCell.setRef(closure);
            pythonClosure[CELL_INDEX_CLOSURE] = closureCell;
            return pythonClosure;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            PCell[] frameClosure = PArguments.getClosure(frame);
            assert frameClosure.length == 2 : "invalid closure for HPyGetSetDescriptorSetterRootNode";

            Object target = ensureReadTargetCellNode().execute(frameClosure[CELL_INDEX_TARGET]);
            Object closure = ensureReadClosureCellNode().execute(frameClosure[CELL_INDEX_CLOSURE]);

            return ensureInvokeNode().execute(frame, name, target, createArguments(frame, closure));
        }

        protected abstract HPyConvertArgsToSulongNode createArgumentConversionNode();

        protected abstract HPyCheckFunctionResultNode createResultConversionNode();

        protected abstract Object[] createArguments(VirtualFrame frame, Object closure);

        @Override
        public String getName() {
            return name;
        }

        private HPyExternalFunctionInvokeNode ensureInvokeNode() {
            if (invokeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invokeNode = insert(HPyExternalFunctionInvokeNodeGen.create(createResultConversionNode(), createArgumentConversionNode()));
            }
            return invokeNode;
        }

        private GetRefNode ensureReadTargetCellNode() {
            if (readTargetCellNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readTargetCellNode = insert(GetRefNode.create());
            }
            return readTargetCellNode;
        }

        private GetRefNode ensureReadClosureCellNode() {
            if (readClosureCellNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readClosureCellNode = insert(GetRefNode.create());
            }
            return readClosureCellNode;
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

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorGetterRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self"}, null);

        HPyGetSetDescriptorGetterRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            return new Object[]{PArguments.getArgument(frame, 0), closure};
        }

        @Override
        protected HPyConvertArgsToSulongNode createArgumentConversionNode() {
            return HPyGetSetGetterToSulongNodeGen.create();
        }

        @Override
        protected HPyCheckFunctionResultNode createResultConversionNode() {
            return HPyCheckHandleResultNodeGen.create();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PFunction createFunction(PythonContext context, String enclosingClassName, String propertyName, Object target, Object closure) {
            // TODO(fa): refactor and use built-in functions as in method 'createLegacyFunction'
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PCell[] pythonClosure = createPythonClosure(target, closure, factory);

            HPyGetSetDescriptorGetterRootNode rootNode = new HPyGetSetDescriptorGetterRootNode(context.getLanguage(), propertyName);
            PCode code = factory.createCode(PythonUtils.getOrCreateCallTarget(rootNode));
            return factory.createFunction(propertyName, enclosingClassName, code, context.getBuiltins(), pythonClosure);
        }

    }

    static final class HPyLegacyGetSetDescriptorGetterRoot extends GetterRoot {

        @Child private HPyGetNativeSpacePointerNode getNativeSpacePointerNode;

        protected HPyLegacyGetSetDescriptorGetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        /*
         * TODO(fa): It's still unclear how to handle HPy native space pointers when passed to an
         * 'AsPythonObjectNode'. This can happen when, e.g., the getter returns the 'self' pointer.
         */
        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] objects = super.prepareCArguments(frame);
            if (getNativeSpacePointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNativeSpacePointerNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            /*
             * We now need to pass the native space pointer in a way that the ToSulongNode correctly
             * exposes the bare pointer object. For this, we pack the pointer into a
             * PythonAbstractNativeObject which will just be unwrapped.
             */
            Object nativeSpacePtr = getNativeSpacePointerNode.execute(objects[0]);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.SystemError, "Attempting to getter function but object has no associated native space.");
            }
            objects[0] = new PythonAbstractNativeObject((TruffleObject) nativeSpacePtr);
            return objects;
        }

        @TruffleBoundary
        public static PBuiltinFunction createLegacyFunction(PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            RootCallTarget rootCallTarget = lang.createCachedCallTarget(l -> new HPyLegacyGetSetDescriptorGetterRoot(l, propertyName, PExternalFunctionWrapper.GETTER),
                            HPyLegacyGetSetDescriptorGetterRoot.class, propertyName);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support in HPy");
            }
            return factory.createBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native setter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorSetterRootNode extends HPyGetSetDescriptorRootNode {
        static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self", "value"}, null);

        private HPyGetSetDescriptorSetterRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            return new Object[]{PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1), closure};
        }

        @Override
        protected HPyConvertArgsToSulongNode createArgumentConversionNode() {
            return HPyGetSetSetterToSulongNodeGen.create();
        }

        @Override
        protected HPyCheckFunctionResultNode createResultConversionNode() {
            return HPyCheckPrimitiveResultNodeGen.create();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PFunction createFunction(PythonContext context, String propertyName, Object target, Object closure) {
            // TODO(fa): refactor and use built-in functions as in method 'createLegacyFunction'
            HPyGetSetDescriptorSetterRootNode rootNode = new HPyGetSetDescriptorSetterRootNode(context.getLanguage(), propertyName);
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PCell[] pythonClosure = createPythonClosure(target, closure, factory);
            PCode code = factory.createCode(PythonUtils.getOrCreateCallTarget(rootNode));
            return factory.createFunction(propertyName, "", code, context.getBuiltins(), pythonClosure);
        }

    }

    static final class HPyLegacyGetSetDescriptorSetterRoot extends SetterRoot {

        @Child private HPyGetNativeSpacePointerNode getNativeSpacePointerNode;

        protected HPyLegacyGetSetDescriptorSetterRoot(PythonLanguage language, String name, PExternalFunctionWrapper provider) {
            super(language, name, provider);
        }

        @Override
        protected Object[] prepareCArguments(VirtualFrame frame) {
            Object[] objects = super.prepareCArguments(frame);
            if (getNativeSpacePointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNativeSpacePointerNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            /*
             * We now need to pass the native space pointer in a way that the ToSulongNode correctly
             * exposes the bare pointer object. For this, we pack the pointer into a
             * PythonAbstractNativeObject which will just be unwrapped.
             */
            Object nativeSpacePtr = getNativeSpacePointerNode.execute(objects[0]);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.SystemError, "Attempting to setter function but object has no associated native space.");
            }
            objects[0] = new PythonAbstractNativeObject((TruffleObject) nativeSpacePtr);
            return objects;
        }

        @TruffleBoundary
        public static PBuiltinFunction createLegacyFunction(PythonLanguage lang, Object owner, String propertyName, Object target, Object closure) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            RootCallTarget rootCallTarget = lang.createCachedCallTarget(l -> new HPyLegacyGetSetDescriptorSetterRoot(l, propertyName, PExternalFunctionWrapper.SETTER),
                            HPyLegacyGetSetDescriptorSetterRoot.class, propertyName);
            if (rootCallTarget == null) {
                throw CompilerDirectives.shouldNotReachHere("Calling non-native get descriptor functions is not support in HPy");
            }
            return factory.createBuiltinFunction(propertyName, owner, PythonUtils.EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(target, closure), rootCallTarget);
        }
    }

    static final class HPyGetSetDescriptorNotWritableRootNode extends HPyGetSetDescriptorRootNode {

        @Child private PRaiseNode raiseNode;
        @Child private GetClassNode getClassNode;
        @Child private GetNameNode getNameNode;

        private HPyGetSetDescriptorNotWritableRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            Object type = getClassNode.execute(PArguments.getArgument(frame, 0));
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, getName(), getNameNode.execute(type));
        }

        @Override
        protected HPyConvertArgsToSulongNode createArgumentConversionNode() {
            // not required since the 'createArguments' method will throw an error
            return null;
        }

        @Override
        protected HPyCheckFunctionResultNode createResultConversionNode() {
            // not required since the 'createArguments' method will throw an error
            return null;
        }

        @Override
        public Signature getSignature() {
            return HPyGetSetDescriptorSetterRootNode.SIGNATURE;
        }

        @TruffleBoundary
        public static PFunction createFunction(PythonContext context, String enclosingClassName, String propertyName) {
            HPyGetSetDescriptorNotWritableRootNode rootNode = new HPyGetSetDescriptorNotWritableRootNode(context.getLanguage(), propertyName);
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PCode code = factory.createCode(PythonUtils.getOrCreateCallTarget(rootNode));
            // we don't need the closure
            return factory.createFunction(propertyName, enclosingClassName, code, context.getBuiltins(), PythonUtils.NO_CLOSURE);
        }
    }
}
