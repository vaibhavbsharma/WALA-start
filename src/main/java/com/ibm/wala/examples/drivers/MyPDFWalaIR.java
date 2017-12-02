/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.examples.drivers;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.CodeReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.shrikeCT.LocalVariableTableReader;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.ibm.wala.util.graph.dominators.NumberedDominators;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.viz.PDFViewUtil;
import org.apache.commons.io.IOUtils;
import x10.wala.util.NatLoop;
import x10.wala.util.NatLoopSolver;

import java.io.*;
import java.util.*;

/**
 * This simple example application builds a WALA IR and fires off a PDF viewer to visualize a DOT representation.
 */
public class MyPDFWalaIR {

  final public static String PDF_FILE = "ir.pdf";

  /**
   * Usage: PDFWalaIR -appJar [jar file name] -sig [method signature] The "jar file
   * name" should be something like "c:/temp/testdata/java_cup.jar" The signature should be something like
   * "java_cup.lexer.advance()V"
   */
  public static void main(String[] args) throws IOException {
    printVarTable();
    run(args);
  }

  /**
   * @param args -appJar [jar file name] -sig [method signature] The "jar file
   *          name" should be something like "c:/temp/testdata/java_cup.jar" The signature should be something like
   *          "java_cup.lexer.advance()V"
   */
  public static Process run(String[] args) throws IOException {
    validateCommandLine(args);
    return run(args[1], args[3]);
  }

  public static void printVarTable() throws FileNotFoundException {
    String workingDir = System.getProperty("user.dir");
    File temp = new File(workingDir+"/resources/", "VeritestingPerf.class");
    InputStream stream =  new FileInputStream(temp);

    System.out.print("workingDir = " + workingDir);
    //InputStream stream = getResourceAsStream("VeritestingPerf.class");
    if (stream == null)
      System.out.println("Cannot find class file.");

    try {
      byte[] streambytes = IOUtils.toByteArray(stream);
      ClassReader reader = null;
      reader = new ClassReader(streambytes);
      int numberOfMethods = reader.getMethodCount();
      int numberOfFields = reader.getFieldCount();
      System.out.println("number of methods in class is " + numberOfMethods +
              " and the number of fields are: " + numberOfFields);

      for (int i = 0; i < numberOfMethods; i++) {
        System.out.println("now printing field information. Method name = " + reader.getMethodName(i));
        ClassReader.AttrIterator iter = new ClassReader.AttrIterator();
        reader.initMethodAttributeIterator(i, iter);
        CodeReader codeReader = new CodeReader(iter);

        if (iter == null)
          System.out.println("No methods for this class.");
        else {
          for (; iter.isValid(); iter.advance()) {
            System.out.println("printing bytecode here" + codeReader.getMaxLocals());
            int[][] vartable = LocalVariableTableReader.makeVarMap(codeReader);
            System.out.println(vartable);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * @param appJar should be something like "c:/temp/testdata/java_cup.jar"
   * @param methodSig should be something like "java_cup.lexer.advance()V"
   * @throws IOException
   */
  public static Process run(String appJar, String methodSig) throws IOException {
    try {
      if (PDFCallGraph.isDirectory(appJar)) {
        appJar = PDFCallGraph.findJarFiles(new String[] { appJar });
      }
      appJar = System.getenv("TARGET_CLASSPATH") + appJar;

      // Build an AnalysisScope which represents the set of classes to analyze.  In particular,
      // we will analyze the contents of the appJar jar file and the Java standard libraries.
      AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, (new FileProvider())
              .getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

      // Build a class hierarchy representing all classes to analyze.  This step will read the class
      // files and organize them into a tree.
      ClassHierarchy cha = ClassHierarchyFactory.make(scope);

      // Create a name representing the method whose IR we will visualize
      MethodReference mr = StringStuff.makeMethodReference(methodSig);

      // Resolve the method name into the IMethod, the canonical representation of the method information.
      IMethod m = cha.resolveMethod(mr);
      if (m == null) {
        Assertions.UNREACHABLE("could not resolve " + mr);
      }

      // Report bytecode offsets for all instructions
      for(int i=0; i < ((IBytecodeMethod) m).getInstructions().length; i++) {
        System.out.println("bytecode offset(" + i + ", " + ((IBytecodeMethod) m).getInstructions()[i].toString() + ") = " + ((IBytecodeMethod) m).getBytecodeIndex(i));
      }

      // Set up options which govern analysis choices.  In particular, we will use all Pi nodes when
      // building the IR.
      AnalysisOptions options = new AnalysisOptions();
      options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());

      // Create an object which caches IRs and related information, reconstructing them lazily on demand.
      IAnalysisCacheView cache = new AnalysisCacheImpl(options.getSSAOptions());

      // Build the IR and cache it.
      IR ir = cache.getIR(m, Everywhere.EVERYWHERE);

      if (ir == null) {
        Assertions.UNREACHABLE("Null IR for " + m);
      }

      // Report local stack slot information (if it exists) for every WALA IR variable
      SSACFG cfg = ir.getControlFlowGraph();
      ir.visitAllInstructions(new SSAInstruction.Visitor() {
        int count=0;
        void getStackSlots(SSAInstruction ssaInstruction) {
          count++;
          for (int v = 0; v < ssaInstruction.getNumberOfUses(); v++) {
            int valNum = ssaInstruction.getUse(v);
            int[] localNumbers = ir.findLocalsForValueNumber(count, valNum);
            if (localNumbers != null) {
              for (int k = 0; k < localNumbers.length; k++) {
                System.out.println("at pc(" + ssaInstruction +
                        "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ", " +
                        ir.getSymbolTable().isConstant(valNum) + ") uses");
              }
            }
          }
          for (int v = 0; v < ssaInstruction.getNumberOfDefs(); v++) {
            int valNum = ssaInstruction.getDef(v);
            int[] localNumbers = ir.findLocalsForValueNumber(count, valNum);
            if (localNumbers != null) {
              for (int k = 0; k < localNumbers.length; k++) {
                System.out.println("at pc(" + ssaInstruction +
                        "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ", " +
                        ir.getSymbolTable().isConstant(valNum) + ") defs");
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
      /* for (int i = 0; i <= cfg.getMaxNumber(); i++) {
        SSACFG.BasicBlock bb = cfg.getNode(i);
        List<SSAInstruction> instructions = bb.getAllInstructions();
        for (int j = 0; j < instructions.size(); j++) {
          if (instructions.get(j) != null) {
            System.out.println("SSAInstruction = " + instructions.get(j));
            for (int v = 0; v < instructions.get(j).getNumberOfUses(); v++) {
              int valNum = instructions.get(j).getUse(v);
              int[] localNumbers = ir.findLocalsForValueNumber(j, valNum);
              if (localNumbers != null) {
                for (int k = 0; k < localNumbers.length; k++) {
                  System.out.println("at pc(" +
                          instructions.get(j) +
                          "), valNum(" + valNum + ") is local var(" + localNumbers[k] + ")");
                }
              }
              if (ir.getLocalNames(j, valNum) != null) {
                for (String s : ir.getLocalNames(j, valNum)) {
                  System.out.println(s);
                }
              }
            }
          }
        }
      }*/
      ArrayList<String> domStr = new ArrayList<>();
        /*cfg.removeExceptionalEdgesToNode(cfg.exit().getNumber());
        Graph<ISSABasicBlock> invertedCFG = GraphInverter.invert(cfg);
        Iterator<ISSABasicBlock> c = cfg.getExceptionalPredecessors(cfg.exit()).iterator();
        while (c.hasNext()) {
          System.out.println("exceptional predecessor: " + c.next());
        }

        System.out.println("invertedCFG = " + invertedCFG.toString());
        NumberedDominators<ISSABasicBlock> dom = (NumberedDominators<ISSABasicBlock>)
                Dominators.make(invertedCFG, cfg.exit());
        Graph<ISSABasicBlock> dominatorTree = dom.dominatorTree();
        System.out.println("dominatorTree = " + dominatorTree.toString());
        System.out.println("-x-x-x-x-\n\n\n\n");


        for (int i = 0; i <= cfg.getMaxNumber(); i++) {
          SSACFG.BasicBlock bb = cfg.getNode(i);
        System.out.println("dominators for " + bb.toString() +":");
        for (Iterator<ISSABasicBlock> it = dominatorTree.getSuccNodes(bb); it.hasNext(); ) {
          SSACFG.BasicBlock bb_dom = (SSACFG.BasicBlock) it.next();
          System.out.println(bb_dom);
        }
          for (int j = 0; j <= cfg.getMaxNumber(); j++) {
            SSACFG.BasicBlock bb1 = cfg.getNode(j);
            if (bb1 == bb) continue;
            if (dom.isDominatedBy(bb, bb1)) {
              //System.out.println(bb1.getNumber() + " dominates " + bb.getNumber());
              domStr.add(bb1.getNumber() + " dominates " + bb.getNumber());
            }
          }
        }*/
      // Report immediate post-dominator of every basic block
      Iterator<ISSABasicBlock> issaBasicBlockIterator = cfg.iterator();
      while(issaBasicBlockIterator.hasNext()) {
        ISSABasicBlock bb = issaBasicBlockIterator.next();
        if (bb != null) {
          domStr.add("IPDom(" + bb.getNumber() + ") = " + cfg.getIPdom(bb.getNumber()));
          Iterator<SSAInstruction> iterator = bb.iterator();
          System.out.println("BB(" + bb.getNumber() + ") instructions:");
          while (iterator.hasNext()) {
            System.out.println(iterator.next());
          }
        } else System.out.println("BB was null");
      }
      Collections.sort(domStr.subList(1, domStr.size()));
      for (int i = 0; i < domStr.size(); i++) {
        System.out.println(domStr.get(i));
      }

      System.out.println("printing loops now");
      NumberedDominators<ISSABasicBlock> uninverteddom = (NumberedDominators<ISSABasicBlock>) Dominators.make(cfg, cfg.entry());
      HashSet<NatLoop> loops = new HashSet<>();
      HashSet<Integer> visited = new HashSet<>();

      NatLoopSolver.findAllLoops(cfg, uninverteddom,loops,visited,cfg.getNode(0));
      NatLoopSolver.printAllLoops(loops);
      System.out.println("printing loops done");

      Properties wp = null;
      try {
        wp = WalaProperties.loadProperties();
        wp.putAll(WalaExamplesProperties.loadProperties());
      } catch (WalaException e) {
        e.printStackTrace();
        Assertions.UNREACHABLE();
      }
      String psFile = wp.getProperty(WalaProperties.OUTPUT_DIR) + File.separatorChar + PDFWalaIR.PDF_FILE;
      String dotFile = wp.getProperty(WalaProperties.OUTPUT_DIR) + File.separatorChar + PDFTypeHierarchy.DOT_FILE;
      String dotExe = wp.getProperty(WalaExamplesProperties.DOT_EXE);
      String gvExe = wp.getProperty(WalaExamplesProperties.PDFVIEW_EXE);

      return PDFViewUtil.ghostviewIR(cha, ir, psFile, dotFile, dotExe, gvExe);

    } catch (WalaException e) {
      e.printStackTrace();
      return null;
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Validate that the command-line arguments obey the expected usage.
   *
   * Usage:
   * <ul>
   * <li>args[0] : "-appJar"
   * <li>args[1] : something like "c:/temp/testdata/java_cup.jar"
   * <li>args[2] : "-sig"
   * <li> args[3] : a method signature like "java_cup.lexer.advance()V" </ul?
   *
   * @param args
   * @throws UnsupportedOperationException if command-line is malformed.
   */
  public static void validateCommandLine(String[] args) {
    if (args.length != 4) {
      throw new UnsupportedOperationException("must have at exactly 4 command-line arguments");
    }
    if (!args[0].equals("-appJar")) {
      throw new UnsupportedOperationException("invalid command-line, args[0] should be -appJar, but is " + args[0]);
    }
    if (!args[2].equals("-sig")) {
      throw new UnsupportedOperationException("invalid command-line, args[2] should be -sig, but is " + args[0]);
    }
  }
}
