package com.ibm.wala.examples.util;

import com.ibm.wala.ssa.*;

import java.util.*;

public class VarUtil {
    String className;
    String methodName;
    IR ir;
    // Maps each WALA IR variable to its corresponding stack slot, if one exists
    HashMap<Integer, Integer> varsMap;
    public HashSet<Integer> usedLocalVars, intermediateVars;
    // Contains how many bytes before an if statement did its operands get populated
    HashMap<Integer, Integer> ifToSetup;
    public VarUtil(IR _ir, String _className, String _methodName) {
        varsMap = new HashMap<> ();
        intermediateVars = new HashSet<Integer> ();
        usedLocalVars = new HashSet<Integer> ();
        ifToSetup = new HashMap<> ();
        className = _className;
        methodName = _methodName;
        ir = _ir;
        // Report local stack slot information (if it exists) for every WALA IR variable
        _ir.visitAllInstructions(new SSAInstruction.Visitor() {
            int count=0;
            void getStackSlots(SSAInstruction ssaInstruction) {
                count++;
                for (int v = 0; v < ssaInstruction.getNumberOfUses(); v++) {
                    int valNum = ssaInstruction.getUse(v);
                    int[] localNumbers = _ir.findLocalsForValueNumber(count, valNum);
                    if (localNumbers != null) {
                        for (int k = 0; k < localNumbers.length; k++) {
                            /*System.out.println("at pc(" + ssaInstruction +
                                    "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ", " +
                                    _ir.getSymbolTable().isConstant(valNum) + ") uses");*/
                            if(!_ir.getSymbolTable().isConstant(valNum))
                                varsMap.put(valNum, localNumbers[k]);
                        }
                    }
                }
                for (int v = 0; v < ssaInstruction.getNumberOfDefs(); v++) {
                    int valNum = ssaInstruction.getDef(v);
                    int[] localNumbers = _ir.findLocalsForValueNumber(count, valNum);
                    if (localNumbers != null) {
                        for (int k = 0; k < localNumbers.length; k++) {
                            /*System.out.println("at pc(" + ssaInstruction +
                                    "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ", " +
                                    _ir.getSymbolTable().isConstant(valNum) + ") defs");*/
                            if(!_ir.getSymbolTable().isConstant(valNum))
                                varsMap.put(valNum, localNumbers[k]);
                        }
                    }
                }
            }
            @Override
            public void visitGoto(SSAGotoInstruction instruction) {
                getStackSlots(instruction);
                super.visitGoto(instruction);
            }

            @Override
            public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
                getStackSlots(instruction);
                super.visitArrayLoad(instruction);
            }

            @Override
            public void visitArrayStore(SSAArrayStoreInstruction instruction) {
                getStackSlots(instruction);
                super.visitArrayStore(instruction);
            }

            @Override
            public void visitBinaryOp(SSABinaryOpInstruction instruction) {
                getStackSlots(instruction);
                super.visitBinaryOp(instruction);
            }

            @Override
            public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
                getStackSlots(instruction);
                super.visitUnaryOp(instruction);
            }

            @Override
            public void visitConversion(SSAConversionInstruction instruction) {
                getStackSlots(instruction);
                super.visitConversion(instruction);
            }

            @Override
            public void visitComparison(SSAComparisonInstruction instruction) {
                getStackSlots(instruction);
                super.visitComparison(instruction);
            }

            @Override
            public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
                getStackSlots(instruction);
                super.visitConditionalBranch(instruction);
            }

            @Override
            public void visitSwitch(SSASwitchInstruction instruction) {
                getStackSlots(instruction);
                super.visitSwitch(instruction);
            }

            @Override
            public void visitReturn(SSAReturnInstruction instruction) {
                getStackSlots(instruction);
                super.visitReturn(instruction);
            }

            @Override
            public void visitGet(SSAGetInstruction instruction) {
                getStackSlots(instruction);
                super.visitGet(instruction);
            }

            @Override
            public void visitPut(SSAPutInstruction instruction) {
                getStackSlots(instruction);
                super.visitPut(instruction);
            }

            @Override
            public void visitInvoke(SSAInvokeInstruction instruction) {
                super.visitInvoke(instruction);
            }

            @Override
            public void visitNew(SSANewInstruction instruction) {
                getStackSlots(instruction);
                super.visitNew(instruction);
            }

            @Override
            public void visitArrayLength(SSAArrayLengthInstruction instruction) {
                getStackSlots(instruction);
                super.visitArrayLength(instruction);
            }

            @Override
            public void visitThrow(SSAThrowInstruction instruction) {
                getStackSlots(instruction);
                super.visitThrow(instruction);
            }

            @Override
            public void visitMonitor(SSAMonitorInstruction instruction) {
                getStackSlots(instruction);
                super.visitMonitor(instruction);
            }

            @Override
            public void visitCheckCast(SSACheckCastInstruction instruction) {
                getStackSlots(instruction);
                super.visitCheckCast(instruction);
            }

            @Override
            public void visitInstanceof(SSAInstanceofInstruction instruction) {
                getStackSlots(instruction);
                super.visitInstanceof(instruction);
            }

            @Override
            public void visitPhi(SSAPhiInstruction instruction) {
                getStackSlots(instruction);
                super.visitPhi(instruction);
            }

            @Override
            public void visitPi(SSAPiInstruction instruction) {
                getStackSlots(instruction);
                super.visitPi(instruction);
            }

            @Override
            public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
                getStackSlots(instruction);
                super.visitGetCaughtException(instruction);
            }

            @Override
            public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
                count++;
                super.visitLoadMetadata(instruction);
            }
        });
    }

    public void addVal(int val) {
        if(ir.getSymbolTable().isConstant(val)) return;
        if(isLocalVariable(val)) addUsedLocalVar(val);
        else addIntermediateVar(val);
    }

    public boolean isLocalVariable(int val) {
        return varsMap.containsKey(val);
    }
    public int getLocalVarSlot(int val) {
        if(isLocalVariable(val)) return varsMap.get(val);
        else return -1;
    }
    public void addUsedLocalVar(int varName) {
        usedLocalVars.add(varName);
        return;
    }
    public void addIntermediateVar(int varName) {
        intermediateVars.add(varName);
        return;
    }
    public void resetUsedLocalVars() {
        // G.v().out.println("resetUsedLocalVars");
        usedLocalVars = new HashSet<Integer> ();
    }
    public void resetIntermediateVars() { intermediateVars = new HashSet<Integer> (); }

    public int getOffsetFromLine(String line) {
        int p1 = line.indexOf(':');
        int p2 = line.substring(0,p1).lastIndexOf(' ');
        return Integer.parseInt(line.substring(p2+1,p1));
    }
    public int getSetupInsn(int offset) {
        if(ifToSetup.containsKey(offset)) return ifToSetup.get(offset);
        else return -1;
    }

    public String getValueString(int use) {
        return ir.getSymbolTable().isConstant(use) ? Integer.toString(ir.getSymbolTable().getIntValue(use)) : ir.getSymbolTable().getValueString(use);
    }
}

