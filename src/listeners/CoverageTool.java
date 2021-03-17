/*
 * Copyright (C) 2014, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The Java Pathfinder core (jpf-core) platform is licensed under the
 * Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package gov.nasa.jpf.listener;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPFConfigException;
import gov.nasa.jpf.jvm.bytecode.*;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.FieldSpec;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ClassLoaderInfo;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ThreadInfo;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import java.io.FileWriter;
import java.io.IOException;

import gov.nasa.jpf.util.StringSetMatcher;

/**
 * little listener that builds a coverage map
 *
 * configuration examples:
 *
 */
public class CoverageTool extends ListenerAdapter {

  VM vm;

  public class Instr_Entry implements Comparable<Instr_Entry> {
    String fileName;
    int lineNo;
    public Instr_Entry(String fileName, int lineNo) {
        this.fileName = fileName;
        this.lineNo = lineNo;
    }
    @Override
    public String toString() {
        return (this.fileName+" : "+this.lineNo);
    }
    @Override
    public int compareTo(Instr_Entry compare_ie) {
        return this.lineNo - compare_ie.lineNo;
    }
  }
  public class MethodCoverage implements Comparable<MethodCoverage> {
    MethodInfo mi;
    ArrayList<Instr_Entry> Instr_List;

    MethodCoverage(MethodInfo mi) {
      this.mi = mi;
    }
    MethodInfo getMethodInfo() {
      return mi;
    }
    ArrayList<Instr_Entry> getInstrList() {
      return this.Instr_List;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(Instr_Entry ie : Instr_List) {
            sb.append(ie.toString()+"\n");
        }
        return sb.toString();
    }
    @Override
    public int compareTo(MethodCoverage compare_mc) {
        String compareClassName = compare_mc.getMethodInfo().getClassName();
        return this.getMethodInfo().getClassName().toLowerCase().compareTo(compareClassName.toLowerCase());
    }
    void getExecInstr() {
      Instruction[] code = mi.getInstructions();
      if(Instr_List == null) { Instr_List = new ArrayList<Instr_Entry>(); }
      for(int i = 0; i < code.length; i++) {
        String[] str_arr = code[i].getFilePos().split(":",2);
        String fileName = str_arr[0];
        int lineNum = Integer.parseInt(str_arr[1]);
        Instr_Entry ie = new Instr_Entry(fileName, lineNum);
        Instr_List.add(ie);
      }
    }
    void mergeInstrList(MethodCoverage mc) {
        Instr_List.addAll(mc.getInstrList());
    }
    void remove_dup() {
        int track_lineNo = -1;
        Collections.sort(Instr_List);

        for(int i = 0; i < Instr_List.size(); i++) {
            int lineNo = Instr_List.get(i).lineNo;
            if(lineNo == track_lineNo) {
                Instr_List.remove(i);
                i--;
            } else {
                track_lineNo = lineNo;
            }
        }
    }
  }

  MethodInfo lastMethod = null;
  MethodCoverage lastMethodCov = null;
  ArrayList<String> class_method_list = null;
  boolean target_class_found = false;
  ArrayList<MethodCoverage> method_cov_list;

  void insert_methodcoverage(MethodCoverage mc) {
    if(0 == method_cov_list.size()) {
        method_cov_list.add(mc);
    } else {
        String className = mc.getMethodInfo().getClassName();
        for(MethodCoverage mcc : method_cov_list) {
            String classNameCpy = mcc.getMethodInfo().getClassName();
            if(0 == className.compareTo(classNameCpy)) {
                mcc.mergeInstrList(mc);
                return;
            }
        }
        method_cov_list.add(mc);
    }
  }
  MethodCoverage getMethodCoverage(Instruction insn) {
    if(!insn.isExtendedInstruction()) {
      MethodInfo mi = insn.getMethodInfo();//Have we already done this method before?
      if(lastMethod != mi) {
        lastMethod = mi;
        lastMethodCov = null;
        ClassInfo ci = mi.getClassInfo();
        if(ci != null && isCoveredClass(mi)
        && !class_method_list.contains(mi.getClassName()+" : "+mi.getName())) {
            MethodCoverage mc = new MethodCoverage(mi);
            for(String s : ci.getInnerClasses()) {
                includes.addPattern(s);
            }
            mc.getExecInstr();
            class_method_list.add(mi.getClassName()+" : "+mi.getName());
            insert_methodcoverage(mc);
        }
      }
      return lastMethodCov;
    }
    return null;
  }
  @Override
  public void instructionExecuted (VM vm, ThreadInfo ti, Instruction nextInsn, Instruction executedInsn){
    this.vm = vm;
    getMethodCoverage(executedInsn);
  }
  @Override
  public void searchFinished(Search search){
    try {
        FileWriter fw = new FileWriter("outputs/result.txt", false);
        Collections.sort(method_cov_list);
        for(MethodCoverage mc : method_cov_list) {
            fw.append("Class: "+mc.getMethodInfo().getClassName()+"\n");
            mc.remove_dup();
            fw.append(mc.toString());
        }
        fw.close();
    } catch (IOException ioe) {
    }
  }
  StringSetMatcher includes = null;
  StringSetMatcher excludes = null; 
  public CoverageTool(Config conf, JPF jpf) {
    includes = StringSetMatcher.getNonEmpty(conf.getStringArray("target"));
    class_method_list = new ArrayList<String>();
    method_cov_list = new ArrayList<MethodCoverage>();
  }
  boolean isCoveredClass(MethodInfo mi) {
    if(target_class_found || StringSetMatcher.isMatch(mi.getClassName(), includes, null)) {
        target_class_found = true;
        return true;
    } else {
        return false;
    }
  }
  
}
