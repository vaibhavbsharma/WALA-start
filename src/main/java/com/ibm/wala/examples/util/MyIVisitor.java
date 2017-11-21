package com.ibm.wala.examples.util;

import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.ssa.*;


public class MyIVisitor implements SSAInstruction.IVisitor {
    boolean isPhiInstruction = false;
    VarUtil varUtil;
    SSAInstruction lastInstruction;
    private String phiExpr0;
    private String phiExpr1;
    private String phiExprLHS;

    public boolean canVeritest() {
        return canVeritest;
    }

    private boolean canVeritest;

    public String getIfExprStr_SPF() {
        return ifExprStr_SPF;
    }

    public String getIfNotExprStr_SPF() {
        return ifNotExprStr_SPF;
    }

    private String ifExprStr_SPF, ifNotExprStr_SPF;

    private String SPFExpr;

    public MyIVisitor(VarUtil _varUtil) {
        varUtil = _varUtil;
        SPFExpr = new String();
    }

    @Override
    public void visitGoto(SSAGotoInstruction instruction) {
        System.out.println("SSAGotoInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = true;
    }

    @Override
    public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
        System.out.println("SSAArrayLoadInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitArrayStore(SSAArrayStoreInstruction instruction) {
        System.out.println("SSAArrayStoreInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitBinaryOp(SSABinaryOpInstruction instruction) {
        System.out.println("SSABinaryOpInstruction = " + instruction);
        lastInstruction = instruction;
        //TODO: make SPFExpr
        canVeritest = false;
    }

    @Override
    public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
        System.out.println("SSAUnaryOpInstruction = " + instruction);
        lastInstruction = instruction;
        //TODO: make SPFExpr
        canVeritest = false;
    }

    @Override
    public void visitConversion(SSAConversionInstruction instruction) {
        System.out.println("SSAConversionInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitComparison(SSAComparisonInstruction instruction) {
        System.out.println("SSAComparisonInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
        System.out.println("SSAConditionalBranchInstruction = " + instruction);
        lastInstruction = instruction;
        if(!instruction.isIntegerComparison()) { canVeritest=false; return; }
        IConditionalBranchInstruction.IOperator op = instruction.getOperator();
        String opString = new String();
        String opNotString = new String();
        if (op.equals(IConditionalBranchInstruction.Operator.NE)) {
            opString = "NE";
            opNotString = "EQ";
        } else if (op.equals(IConditionalBranchInstruction.Operator.EQ)) {
            opString = "EQ";
            opNotString = "NE";
        } else if (op.equals(IConditionalBranchInstruction.Operator.LE)) {
            opString = "LE";
            opNotString = "GT";
        } else if (op.equals(IConditionalBranchInstruction.Operator.LT)) {
            opString = "LT";
            opNotString = "GE";
        } else if (op.equals(IConditionalBranchInstruction.Operator.GE)) {
            opString = "GE";
            opNotString = "LT";
        } else if (op.equals(IConditionalBranchInstruction.Operator.GT)) {
            opString = "GT";
            opNotString = "LE";
        }
        ifExprStr_SPF = "new ComplexNonLinearIntegerExpression(" +
                varUtil.getValueString(instruction.getUse(0)) + ", " + opString + ", " +
                varUtil.getValueString(instruction.getUse(1)) + ")";
        ifNotExprStr_SPF = "new ComplexNonLinearIntegerExpression(" +
                varUtil.getValueString(instruction.getUse(0)) + ", " + opNotString + ", " +
                varUtil.getValueString(instruction.getUse(1)) + ")";
        varUtil.addVal(instruction.getUse(0));
        varUtil.addVal(instruction.getUse(1));
        canVeritest=true;
    }

    @Override
    public void visitSwitch(SSASwitchInstruction instruction) {
        System.out.println("SSASwitchInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitReturn(SSAReturnInstruction instruction) {
        System.out.println("SSAReturnInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitGet(SSAGetInstruction instruction) {
        System.out.println("SSAGetInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitPut(SSAPutInstruction instruction) {
        System.out.println("SSAPutInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitInvoke(SSAInvokeInstruction instruction) {
        System.out.println("SSAInvokeInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitNew(SSANewInstruction instruction) {
        System.out.println("SSANewInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitArrayLength(SSAArrayLengthInstruction instruction) {
        System.out.println("SSAArrayLengthInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitThrow(SSAThrowInstruction instruction) {
        System.out.println("SSAThrowInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitMonitor(SSAMonitorInstruction instruction) {
        System.out.println("SSAMonitorInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitCheckCast(SSACheckCastInstruction instruction) {
        System.out.println("SSACheckCastInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitInstanceof(SSAInstanceofInstruction instruction) {
        System.out.println("SSAInstanceofInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitPhi(SSAPhiInstruction instruction) {
        isPhiInstruction = true;
        System.out.println("SSAPhiInstruction = " + instruction);
        lastInstruction = instruction;
        assert(instruction.getNumberOfUses()==2);
        assert(instruction.getNumberOfDefs()==1);
        phiExpr0 = varUtil.getValueString(instruction.getUse(0));
        phiExpr1 = varUtil.getValueString(instruction.getUse(1));
        phiExprLHS = varUtil.getValueString(instruction.getDef(0));
        assert(varUtil.ir.getSymbolTable().isConstant(instruction.getDef(0)) == false);
        varUtil.addVal(instruction.getUse(0));
        varUtil.addVal(instruction.getUse(1));
        varUtil.addVal(instruction.getDef(0));
    }

    @Override
    public void visitPi(SSAPiInstruction instruction) {
        System.out.println("SSAPiInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
        System.out.println("SSAGetCaughtExceptionInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    @Override
    public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
        System.out.println("SSALoadMetadataInstruction = " + instruction);
        lastInstruction = instruction;
        canVeritest = false;
    }

    public String getSPFExpr() {
        return SPFExpr;
    }

    public String getLastInstruction() {
        return getLastInstruction().toString();
    }

    public String getPhiExprSPF(String thenPLAssignSPF, String elsePLAssignSPF) {
        assert(phiExpr0 != null && phiExpr0 != "");
        assert(phiExpr1 != null && phiExpr1 != "");
        assert(phiExprLHS != null && phiExprLHS != "");
        String phiExpr0_s = StringUtil.nCNLIE + phiExprLHS + ", EQ, " + phiExpr0 + ")";
        String phiExpr1_s = StringUtil.nCNLIE + phiExprLHS + ", EQ, " + phiExpr1 + ")";
        // (pathLabel == 1 && lhs == phiExpr0) || (pathLabel == 2 && lhs == phiExpr1)
        return StringUtil.SPFLogicalOr(
                StringUtil.SPFLogicalAnd( thenPLAssignSPF,
                        phiExpr0_s),
                StringUtil.SPFLogicalAnd( elsePLAssignSPF,
                        phiExpr1_s
                ));
    }
}
