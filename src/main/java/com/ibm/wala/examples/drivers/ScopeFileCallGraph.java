/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.examples.drivers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.examples.util.ExampleUtil;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.util.warnings.Warnings;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.shrikeCT.InvalidClassFileException;


/**
 * Driver that constructs a call graph for an application specified via a scope file.  
 * Useful for getting some code to copy-paste.    
 */
public class ScopeFileCallGraph {

  /**
   * Usage: ScopeFileCallGraph -scopeFile file_path [-entryClass class_name |
   * -mainClass class_name]
   * 
   * If given -mainClass, uses main() method of class_name as entrypoint. If
   * given -entryClass, uses all public methods of class_name.
   * 
   * @throws IOException
   * @throws ClassHierarchyException
   * @throws CallGraphBuilderCancelException
   * @throws IllegalArgumentException
   */
  public static void main(String[] args) throws IOException, ClassHierarchyException, IllegalArgumentException {
      long start = System.currentTimeMillis();
      Properties p = CommandLine.parse(args);
      String scopeFile = p.getProperty("scopeFile");
      String entryClass = p.getProperty("entryClass");
      String mainClass = p.getProperty("mainClass");
      if (mainClass != null && entryClass != null) {
          throw new IllegalArgumentException("only specify one of mainClass or entryClass");
      }
      AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopeFile, null, ScopeFileCallGraph.class.getClassLoader());
      // set exclusions.  we use these exclusions as standard for handling JDK 8
      ExampleUtil.addDefaultExclusions(scope);
      IClassHierarchy cha = ClassHierarchyFactory.make(scope);

      System.out.println(cha.getNumberOfClasses() + " classes");
      //System.out.println(Warnings.asString());
      Warnings.clear();


      AnalysisOptions options = new AnalysisOptions();

      String methodSig = "VeritestingPerf.testMe4([II)V";
      MethodReference mr = StringStuff.makeMethodReference(Language.JAVA, methodSig);
      IMethod m = cha.resolveMethod(mr);
      if (m == null) {
          Assertions.UNREACHABLE("could not resolve " + mr);
      }
      options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
      IRFactory<IMethod> factory = AstIRFactory.makeDefaultFactory();
      IR ir = factory.makeIR(m, Everywhere.EVERYWHERE, options.getSSAOptions());
      System.out.println(ir);
      IBytecodeMethod method = (IBytecodeMethod) ir.getMethod();
      try {
          int bytecodeIndex = method.getBytecodeIndex(12);
          System.out.println("bytecode index is : " + bytecodeIndex);
          int sourceLineNum = method.getLineNumber(bytecodeIndex);
          System.out.println("Source line code is:" + sourceLineNum);
      } catch (Exception InvalidClassFileException) {
          System.out.print("invalid index");
      }
      System.out.println("Now checking the symbol table:");
      SymbolTable symtab = ir.getSymbolTable();
      System.out.println(symtab.toString());


      /////////////////
      List<String> list = new ArrayList<String>();
      System.out.println("--------Printing all available values--------");
      for (int i = 1; i < symtab.getMaxValueNumber(); i++) {
          if (symtab.getValue(i) != null) {
              list.add(symtab.getValue(i).toString());

          }
          System.out.println(" | Symbol Value="
                  + symtab.getValueString(i));
          if (symtab.isConstant(i) == true) {
              System.out.println(" |isConstant =" + symtab.isConstant(i));
          }
      }

      System.out.println(list);
      /////////////////

      System.out.println("finished");
/*
      Iterable<Entrypoint> entrypoints = entryClass != null ? makePublicEntrypoints(scope, cha, entryClass) : Util.makeMainEntrypoints(scope, cha, mainClass);
      options.setEntrypoints(entrypoints);
      // you can dial down reflection handling if you like
      //    options.setReflectionOptions(ReflectionOptions.NONE);
      AnalysisCache cache = new AnalysisCacheImpl();
      // other builders can be constructed with different Util methods
      CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, cache, cha, scope);
      //    CallGraphBuilder builder = Util.makeNCFABuilder(2, options, cache, cha, scope);
      //    CallGraphBuilder builder = Util.makeVanillaNCFABuilder(2, options, cache, cha, scope);
      System.out.println("building call graph...");
      CallGraph cg = builder.makeCallGraph(options, null);
      // CGNode node = cg.getNode();
      // node.getIR();

      long end = System.currentTimeMillis();
      System.out.println("done");
      System.out.println("took " + (end-start) + "ms");
      System.out.println(CallGraphStats.getStats(cg));


  }

  private static Iterable<Entrypoint> makePublicEntrypoints(AnalysisScope scope, IClassHierarchy cha, String entryClass) {
    Collection<Entrypoint> result = new ArrayList<Entrypoint>();
    IClass klass = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application,
        StringStuff.deployment2CanonicalTypeString(entryClass)));
    for (IMethod m : klass.getDeclaredMethods()) {
      if (m.isPublic()) {
        result.add(new DefaultEntrypoint(m, cha));
      }
    }
    return result;
  }*/

  }
}
