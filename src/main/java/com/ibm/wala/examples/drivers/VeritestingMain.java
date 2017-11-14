package com.ibm.wala.examples.drivers;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;

import java.io.IOException;
import java.util.*;

public class VeritestingMain {

    public static int pathLabelVarNum=0;
    public static HashSet endingInsnsHash;
    public static void main(String[] args) {
        endingInsnsHash = new HashSet();
        new MyAnalysis(args[1], args[3]);
    }

    public static class MyAnalysis {
        SSACFG cfg;
        HashSet startingPointsHistory;
        String className, methodName;
        public MyAnalysis(String appJar, String methodSig) {
            try {
                appJar = System.getenv("TARGET_CLASSPATH") + appJar;
                AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar,
                        (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
                ClassHierarchy cha = ClassHierarchyFactory.make(scope);
                MethodReference mr = StringStuff.makeMethodReference(methodSig);
                IMethod m = cha.resolveMethod(mr);
                if (m == null) {
                    Assertions.UNREACHABLE("could not resolve " + mr);
                }
                AnalysisOptions options = new AnalysisOptions();
                options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
                IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());
                IR ir = cache.getIR(m, Everywhere.EVERYWHERE);
                if (ir == null) {
                    Assertions.UNREACHABLE("Null IR for " + m);
                }
                cfg = ir.getControlFlowGraph();
                className = m.getClass().getName().toString();
                methodName = m.getName().toString();
                System.out.println("Starting analysis for " + methodName);
                doAnalysis(cfg.entry(), null);
            } catch (WalaException|InvalidClassFileException|IOException e) {
                e.printStackTrace();
            }
        }

        private void printTags(Stmt stmt) {
            Iterator tags_it = stmt.getTags().iterator();
            while(tags_it.hasNext()) G.v().out.println(tags_it.next());
            G.v().out.println("  end tags");
        }

        public Unit getIPDom(Unit u) {
            MHGPostDominatorsFinder m = new MHGPostDominatorsFinder(g);
            Unit u_IPDom = (Unit) m.getImmediateDominator(u);
            return u_IPDom;
        }


        public void printSPFExpr(String thenExpr, String elseExpr, final String thenPLAssignSPF, final String elsePLAssignSPF,
                                 String if_SPFExpr, String ifNot_SPFExpr,
                                 Unit currUnit, Unit commonSucc) {
            thenExpr = MyUtils.SPFLogicalAnd(thenExpr, thenPLAssignSPF);
            elseExpr = MyUtils.SPFLogicalAnd(elseExpr, elsePLAssignSPF);

            // (If && thenExpr) || (ifNot && elseExpr)
            String pathExpr1 =
                    MyUtils.SPFLogicalOr(
                            MyUtils.SPFLogicalAnd(if_SPFExpr, thenExpr),
                            MyUtils.SPFLogicalAnd(ifNot_SPFExpr, elseExpr));

            final StringBuilder sB = new StringBuilder();
            final StringBuilder setSlotAttr_SB = new StringBuilder();
            final ArrayList<String> lhs_SB = new ArrayList();
            commonSucc.apply(new AbstractStmtSwitch() {
                public void caseAssignStmt(AssignStmt stmt) {
                    String lhs = stmt.getLeftOp().toString();
                    lhs_SB.add(lhs);
                    String s_tmp = new String("sf.setSlotAttr("+
                            lvt.getLocalVarSlot(lhs.substring(0,lhs.length()-2))+
                            ", " + lhs + ");");
                    setSlotAttr_SB.append(s_tmp);
                    MyShimpleValueSwitch msvs = new MyShimpleValueSwitch(lvt);
                    stmt.getRightOp().apply(msvs);
                    String phiExpr0 = msvs.getArg0PhiExpr();
                    String phiExpr1 = msvs.getArg1PhiExpr();

                    String phiExpr0_s = MyUtils.nCNLIE + lhs + ", EQ, " + phiExpr0 + ")";
                    String phiExpr1_s = MyUtils.nCNLIE + lhs + ", EQ, " + phiExpr1 + ")";
                    // (pathLabel == 1 && lhs == phiExpr0) || (pathLabel ==2 && lhs == phiExpr1)
                    sB.append( MyUtils.SPFLogicalOr(
                            MyUtils.SPFLogicalAnd( thenPLAssignSPF,
                                    phiExpr0_s),
                            MyUtils.SPFLogicalAnd( elsePLAssignSPF,
                                    phiExpr1_s
                            )));
                }});
            String startingInsn = currUnit.getTag("BytecodeOffsetTag").toString();
            String endingInsn;
            Unit savedCommonSucc = commonSucc;
            while(true) {
                Tag t = commonSucc.getTag("BytecodeOffsetTag");
                if(t == null) {
                    if(commonSucc != savedCommonSucc) {
                        commonSucc.apply(new AbstractStmtSwitch() {
                            public void caseAssignStmt(AssignStmt stmt) {
                                String lhs = stmt.getLeftOp().toString();
                                lhs_SB.add(lhs);
                                String s_tmp = new String("\nsf.setSlotAttr("+
                                        lvt.getLocalVarSlot(lhs.substring(0,lhs.length()-2))+
                                        ", " + lhs + ");");
                                setSlotAttr_SB.append(s_tmp);
                                MyShimpleValueSwitch msvs = new MyShimpleValueSwitch(lvt);
                                stmt.getRightOp().apply(msvs);
                                String phiExpr0 = msvs.getArg0PhiExpr();
                                String phiExpr1 = msvs.getArg1PhiExpr();

                                String phiExpr0_s = MyUtils.nCNLIE + lhs + ", EQ, " + phiExpr0 + ")";
                                String phiExpr1_s = MyUtils.nCNLIE + lhs + ", EQ, " + phiExpr1 + ")";
                                // (pathLabel == 1 && lhs == phiExpr0) || (pathLabel ==2 && lhs == phiExpr1)
                                String tmpStr = MyUtils.SPFLogicalAnd(sB.toString(),
                                        MyUtils.SPFLogicalOr(
                                                MyUtils.SPFLogicalAnd( thenPLAssignSPF,
                                                        phiExpr0_s),
                                                MyUtils.SPFLogicalAnd( elsePLAssignSPF,
                                                        phiExpr1_s
                                                )));
                                sB.setLength(0);
                                sB.append(tmpStr);
                            }});
                    }
                    commonSucc = g.getUnexceptionalSuccsOf(commonSucc).get(0);
                } else {
                    endingInsn = t.toString();
                    int eiSetupInsn = lvt.getSetupInsn(Integer.parseInt(endingInsn));
                    if(eiSetupInsn!= -1) {
                        endingInsn = Integer.toString(eiSetupInsn);
                    }
                    break;
                }
            }
            String finalPathExpr = MyUtils.SPFLogicalAnd(pathExpr1, sB.toString());
            // G.v().out.println("At offset = " + startingInsn +
            //     " finalPathExpr = "+finalPathExpr);
            // G.v().out.println("lvt.usedLocalVars.size = " + lvt.usedLocalVars.size());

            // Generate the executeInstruction listener function
            String fn = "public void " + className +
                    "_" + methodName + "_VT_" + startingInsn + "_" + endingInsn + "\n";
            fn += " (VM vm, ThreadInfo ti, Instruction instructionToExecute) {\n";
            fn += "  if(ti.getTopFrame().getPC().getPosition() == " + startingInsn + " && \n";
            fn += "     ti.getTopFrame().getMethodInfo().getName().equals(\"" + methodName + "\") && \n";
            fn += "     ti.getTopFrame().getClassInfo().getName().equals(\"" + className + "\")) {\n";
            fn += "    StackFrame sf = ti.getTopFrame();\n";
            Iterator it = lvt.usedLocalVars.iterator();
            while(it.hasNext()) {
                String s = (String) it.next();
                int slot = lvt.getLocalVarSlot(s);
                if(slot!=-1) {
                    fn += "    SymbolicInteger "+s+" = (SymbolicInteger) sf.getLocalAttr("+slot+");\n";
                }
            }
            it = lvt.intermediateVars.iterator();
            while(it.hasNext()) {
                String s = (String) it.next();
                fn += "    SymbolicInteger " + s + " = makeSymbolicInteger(ti.getEnv(), \"" + s + "\");\n";
            }
            for(int lhs_SB_i = 0; lhs_SB_i < lhs_SB.size(); lhs_SB_i++) {
                String tmpStr = lhs_SB.get(lhs_SB_i);
                fn += "    SymbolicInteger " + tmpStr + " = makeSymbolicInteger(ti.getEnv(), \"" + tmpStr + "\");\n";
            }
            fn += "    SymbolicInteger pathLabel" + pathLabelVarNum + " = makeSymbolicInteger(ti.getEnv(), \"pathLabel" + pathLabelVarNum+ "\");\n";
            fn += "    PathCondition pc;\n";
            fn += "    pc = ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).getCurrentPC();\n";
            fn += "    pc._addDet(new ComplexNonLinearIntegerConstraint(\n    " + finalPathExpr + "));\n";
            fn += "    " + setSlotAttr_SB.toString() + "\n";
            fn += "    Instruction insn=instructionToExecute;\n";
            fn += "    while(insn.getPosition() != " + endingInsn + ") {\n";
            fn += "      if(insn instanceof GOTO)  insn = ((GOTO) insn).getTarget();\n";
            fn += "      else insn = insn.getNext();\n";
            fn += "    }";
            if(!endingInsnsHash.contains(startingInsn))
                fn += "    sf.pop(); sf.pop();\n"; // popping the region's starting node (if stmt) operands
            fn += "    ((PCChoiceGenerator) ti.getVM().getSystemState().getChoiceGenerator()).setCurrentPC(pc);\n";
            fn += "    ti.setNextPC(insn);\n";
            fn += "  }\n";
            fn += "}\n";

            G.v().out.println(fn);
            endingInsnsHash.add(endingInsn);

            lvt.resetUsedLocalVars();
            lvt.resetIntermediateVars();
            pathLabelVarNum++;
        }

        public void doAnalysis(ISSABasicBlock startingUnit, ISSABasicBlock endingUnit) {
            G.v().out.println("Starting doAnalysis");
            Unit currUnit = startingUnit;
            MyStmtSwitch myStmtSwitch;
            if(startingPointsHistory.contains(startingUnit)) return;
            while(true) {
                if(currUnit == null || currUnit == endingUnit) break;
                //printTags((Stmt)currUnit);
                // G.v().out.println("BOTag = " + ((Stmt)currUnit).getTag("BytecodeOffsetTag"));
                List<Unit> succs = g.getUnexceptionalSuccsOf(currUnit);
                Unit commonSucc = getIPDom(currUnit);
                if(succs.size()==1) {
                    currUnit = succs.get(0);
                    continue;
                } else if (succs.size()==0)
                    break;
                else if(succs.size() == 2 && startingPointsHistory.contains(currUnit)) {
                    currUnit = commonSucc;
                    break;
                } else if(succs.size() == 2 && !startingPointsHistory.contains(currUnit)) {
                    startingPointsHistory.add(currUnit);
                    lvt.resetUsedLocalVars();
                    lvt.resetIntermediateVars();
                    // G.v().out.printf("  #succs = %d\n", succs.size());
                    myStmtSwitch = new MyStmtSwitch(lvt);
                    currUnit.apply(myStmtSwitch);
                    String if_SPFExpr = myStmtSwitch.getSPFExpr();
                    String ifNot_SPFExpr = myStmtSwitch.getIfNotSPFExpr();
                    Unit thenUnit = succs.get(0); //assuming this order for now
                    Unit elseUnit = succs.get(1);
                    Unit nextUnit = null;
                    if(thenUnit == commonSucc) nextUnit = elseUnit;
                    if(elseUnit == commonSucc) nextUnit = thenUnit;
                    if(nextUnit != null) {
                        doAnalysis(nextUnit, commonSucc);
                        currUnit = commonSucc;
                        continue;
                    }
                    String thenExpr="", elseExpr="";
                    final int thenPathLabel = MyUtils.getPathCounter();
                    final int elsePathLabel = MyUtils.getPathCounter();
                    final String thenPLAssignSPF =
                            MyUtils.nCNLIE + "pathLabel" + pathLabelVarNum +
                                    ", EQ, new IntegerConstant(" + thenPathLabel + "))";
                    final String elsePLAssignSPF =
                            MyUtils.nCNLIE + "pathLabel" + pathLabelVarNum +
                                    ", EQ, new IntegerConstant(" + elsePathLabel + "))";
                    boolean canVeritest = true;

                    // Create thenExpr
                    while(thenUnit != commonSucc) {
                        // G.v().out.println("BOTag = " + ((Stmt)thenUnit).getTag("BytecodeOffsetTag") +
                        //     ", h.size() = " + h.size());
                        myStmtSwitch = new MyStmtSwitch(lvt);
                        thenUnit.apply(myStmtSwitch);
                        if(myStmtSwitch.getCanVeritest() == false || g.getUnexceptionalSuccsOf(thenUnit).size() > 1) {
                            G.v().out.println("(thenUnit) Stmt " + thenUnit + " cannot veritest");
                            canVeritest = false;
                            break;
                        }
                        String thenExpr1 = myStmtSwitch.getSPFExpr();
                        if(thenExpr1 != null && !thenExpr1.equals("")) {
                            if (!thenExpr.equals(""))
                                thenExpr = MyUtils.SPFLogicalAnd(thenExpr, thenExpr1);
                            else thenExpr = thenExpr1;
                        }
                        thenUnit = g.getUnexceptionalSuccsOf(thenUnit).get(0);
                        if(thenUnit == endingUnit) break;
                    }
                    while(canVeritest && (elseUnit != commonSucc)) {
                        // G.v().out.println("BOTag = " + ((Stmt)elseUnit).getTag("BytecodeOffsetTag") +
                        //     ", h.size() = " + h.size());
                        myStmtSwitch = new MyStmtSwitch(lvt);
                        elseUnit.apply(myStmtSwitch);
                        if(myStmtSwitch.getCanVeritest() == false || g.getUnexceptionalSuccsOf(elseUnit).size() > 1) {
                            G.v().out.println("(elseUnit) Stmt " + elseUnit + " cannot veritest");
                            canVeritest = false;
                            break;
                        }
                        String elseExpr1 = myStmtSwitch.getSPFExpr();
                        if(elseExpr1 != null && !elseExpr1.equals("")) {
                            if (!elseExpr.equals(""))
                                elseExpr = MyUtils.SPFLogicalAnd(elseExpr, elseExpr1);
                            else elseExpr = elseExpr1;
                        }
                        elseUnit = g.getUnexceptionalSuccsOf(elseUnit).get(0);
                        if(elseUnit == endingUnit) break;
                    }

                    // Assign pathLabel a value in the elseExpr
                    if(canVeritest)
                        printSPFExpr(thenExpr, elseExpr, thenPLAssignSPF, elsePLAssignSPF,
                                if_SPFExpr, ifNot_SPFExpr, currUnit, commonSucc);
                    currUnit = commonSucc;
                } else {
                    G.v().out.println("more than 2 successors unhandled in stmt = " + currUnit);
                    assert(false);
                }
                G.v().out.println();
            } // end while(true)
            if(currUnit != null && currUnit != startingUnit && currUnit != endingUnit &&
                    g.getUnexceptionalSuccsOf(currUnit).size()>0) doAnalysis(currUnit, endingUnit);
        } // end doAnalysis

    } // end MyAnalysis class

}

