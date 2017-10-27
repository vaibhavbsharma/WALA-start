package com.ibm.wala.examples.drivers;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;


public class WalaTest {
    public static void main(String args[]) throws IOException, ClassHierarchyException {

        File exFile=new FileProvider().getFile("exclusions.txt");
        System.out.println(exFile.getAbsolutePath());
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope("/Users/sohahussein/Research/veritesting/WALA-start/helloworld.jar",exFile);
        ClassHierarchy cha = ClassHierarchyFactory.make(scope);


        for (IClass c : cha) {
            String cname = c.getName().toString();
            System.out.println("Class:" + cname);
           /* for (IMethod m : c.getAllMethods()) {
                String mname = m.getName().toString();
                System.out.println("  method:" + mname);
            }*/
            System.out.println();
        }


    }
}