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

import java.io.*;
import java.util.Properties;

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
import com.ibm.wala.shrikeCT.LocalVariableTableReader;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.viz.PDFViewUtil;
import org.apache.commons.io.IOUtils;

import static com.sun.org.apache.xerces.internal.utils.SecuritySupport.getResourceAsStream;

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
      System.out.println("number of methods in class is " + numberOfMethods + " and the number of fields are: " + numberOfFields);

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

      System.err.println(ir.toString());


      SSACFG cfg = ir.getControlFlowGraph();
      for (int i = 0; i <= cfg.getMaxNumber(); i++) {
        SSACFG.BasicBlock bb = cfg.getNode(i);
        int start = bb.getFirstInstructionIndex();
        int end = bb.getLastInstructionIndex();
        SSAInstruction[] instructions = ir.getInstructions();
        for (int j = start; j <= end; j++) {
          if (instructions[j] != null) {
            for (int v = 0; v < instructions[j].getNumberOfUses(); v++) {
              int valNum = instructions[j].getUse(v);
              int[] localNumbers = ir.findLocalsForValueNumber(j, valNum);
              if (localNumbers != null) {
                for (int k = 0; k < localNumbers.length; k++) {
                  System.out.println("at pc(" + j + "), valNum(" + valNum + ") is local var("+localNumbers[k]+")");
                }
              }
              if(ir.getLocalNames(j, valNum) != null) {
                for (String s : ir.getLocalNames(j, valNum)) {
                  System.out.println(s);
                }
              }
            }
          }
        }
      }

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
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }


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
