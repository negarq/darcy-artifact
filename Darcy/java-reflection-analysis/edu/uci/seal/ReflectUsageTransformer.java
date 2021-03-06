package edu.uci.seal;

import edu.uci.seal.analyses.ApkSceneTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.util.Chain;

import java.io.File;
import java.util.*;


public class ReflectUsageTransformer {

//    Logger logger = LoggerFactory.getLogger(ReflectUsageTransformer.class);
    private static int totalReflectInvokeCount = 0;
    private static int nonStringConstantMethodNameCount = 0;
    private static int methodNameWithoutClassNameCount = 0;
    private static int fullMethodNameCount = 0;
    private static Map<String,Integer> fullMethodCounts = new LinkedHashMap<String,Integer>();
    private static Map<String,Integer> partMethodCounts = new LinkedHashMap<String,Integer>();



    private Set<SootMethod> getMethods(List<String> processDirs){
        Options.v().set_soot_classpath("/Users/negar/Documents/University/UCI/Research/Java9/Reflection/java-reflection-analysis/");
        Options.v().set_whole_program(true);
        Options.v().set_debug(true);
        Options.v().set_allow_phantom_refs(true);

        Options.v().set_coffi(true);

        Options.v().set_process_dir(processDirs);

//        Options.v().set_keep_line_number(true);

        Scene.v().loadNecessaryClasses();

        Set<SootMethod> methods = new LinkedHashSet<SootMethod>();

        for (SootClass clazz : Scene.v().getApplicationClasses()) {
            methods.addAll( clazz.getMethods() );
        }
//        SootClass c = Scene.v().loadClassAndSupport("Sample");
//        Scene.v().loadNecessaryClasses();
//        c.setApplicationClass();
//        Set<SootMethod> classMethods = new LinkedHashSet<SootMethod>();
//        classMethods.addAll(c.getMethods());
//        Chain<SootClass> classes = Scene.v().getClasses();
//        for (SootClass clazz : classes) {
//            clazz.getMethods()
//            classMethods.addAll( clazz.getMethods() );
//        }
        return methods;
    }
    public void internalTransform(List<String> processDirs){

        Set<SootMethod> methods = getMethods(processDirs);

        int currMethodCount = 1;
        System.out.println("total number of possible methods to analyze: " + methods.size());
        for (SootMethod method : methods) {
            System.out.println("Checking if I should analyze method: " + method);
            if (Utils.isApplicationMethod(method)) {
                if (method.isConcrete()) {
                    Body body = method.retrieveActiveBody();
                    PackManager.v().getPack("jtp").apply(body);
                    if( Options.v().validate() ) {
                        body.validate();
                    }
                }
                if (method.hasActiveBody()) {
                    System.out.println("HEREEE:" + method.getName());
                    doAnalysisOnMethod(method);
                }
                else {
                    System.out.println("method " + method + " has no active body, so it's won't be analyzed.");
                }

                if (Thread.interrupted()) {
                    try {
                        throw new InterruptedException();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        return;
                    }
                }

            }
            System.out.println("Finished loop analysis on method: " + method);
            System.out.println("Number of methods analyzed: " + currMethodCount);
            currMethodCount++;
        }
    }


    private void doAnalysisOnMethod(SootMethod method) {
        System.out.println("Analyzing method " + method.getSignature());

        int methodReflectInvokeCount = 0;
        if (method.hasActiveBody()) {
            Body body = method.getActiveBody();
            PatchingChain<Unit> units = body.getUnits();

            UnitGraph unitGraph = new BriefUnitGraph(body);
            SimpleLocalDefs defs = new SimpleLocalDefs(unitGraph);

            for (Unit unit : units) {
                if (unit instanceof JAssignStmt) {
                    JAssignStmt assignStmt = (JAssignStmt)unit;
                    if (assignStmt.getRightOp() instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)assignStmt.getRightOp();
                        methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, defs, assignStmt, invokeExpr);
                    }
                }
                else if (unit instanceof JInvokeStmt) {
                    JInvokeStmt invokeStmt = (JInvokeStmt)unit;
                    if ( checkForReflectInvocation(invokeStmt.getInvokeExpr()) ) {
                        if (invokeStmt.getInvokeExpr() instanceof VirtualInvokeExpr) {
                            VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr)invokeStmt.getInvokeExpr();
                            methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, defs, invokeStmt, virtualInvokeExpr);
                        }
                    }
                }
            }
        }

        totalReflectInvokeCount += methodReflectInvokeCount;
    }

    public int identifyReflectiveCall(SootMethod method, int methodReflectInvokeCount, SimpleLocalDefs defs, Stmt inStmt, VirtualInvokeExpr invokeExpr) {
        if ( checkForReflectInvocation(invokeExpr) ) {
            Hierarchy hierarchy = Scene.v().getActiveHierarchy();
            SootClass classLoaderClass = Utils.getLibraryClass("java.lang.ClassLoader");
            methodReflectInvokeCount++;
            System.out.println(method.getName() + " reflectively invokes "  + invokeExpr.getMethod().getDeclaringClass() + "." + invokeExpr.getMethod().getName());
            if (invokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.reflect.Method")) {
                if (!(invokeExpr.getBase() instanceof Local)) {
                    System.out.println("\tThis reflection API usage invocation has a callee of a non-local class");
                    return methodReflectInvokeCount;
                }
                Local invokeExprLocal = (Local) invokeExpr.getBase();
                List<Unit> defUnits = defs.getDefsOfAt(invokeExprLocal, inStmt);
                for (Unit defUnit : defUnits) {
                    if (!(defUnit instanceof JAssignStmt)) {
                        continue;
                    }
                    JAssignStmt methodAssignStmt = (JAssignStmt) defUnit;
                    if (methodAssignStmt.getRightOp() instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr getDeclaredMethodExpr = (VirtualInvokeExpr)methodAssignStmt.getRightOp();
                        if (getDeclaredMethodExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && MethodConstants.reflectiveGetMethodsSet.contains(getDeclaredMethodExpr.getMethod().getName())) {
                            boolean result = handleReflectiveGetMethods(getDeclaredMethodExpr, methodAssignStmt, defs, inStmt, hierarchy, classLoaderClass);
                            if (!result) {
                                continue;
                            }
                        }
                    }
                    for (ValueBox useBox : methodAssignStmt.getUseBoxes()) {
                        if (useBox.getValue() instanceof FieldRef) {
                            System.out.println("\t\t" + useBox + " is instanceof FieldRef");
                            FieldRef fieldRef = (FieldRef)useBox.getValue();
                            for (Tag tag : fieldRef.getField().getTags()) {
                                System.out.println("\t\t\ttag: " + tag);
                            }
                        }
                    }
                }
            }
        }
        return methodReflectInvokeCount;
    }

    private boolean checkForReflectInvocation(InvokeExpr invokeExpr) {
        return invokeExpr.getMethod().getDeclaringClass().getPackageName().startsWith("java.lang.reflect");
    }

    private boolean handleReflectiveGetMethods(VirtualInvokeExpr getDeclaredMethodExpr, JAssignStmt methodAssignStmt, SimpleLocalDefs defs, Stmt inStmt, Hierarchy hierarchy, SootClass classLoaderClass) {
        if (!(getDeclaredMethodExpr.getArg(0) instanceof StringConstant)) {
            System.out.println("Reflective invocation is not a string constant at " + methodAssignStmt);
            nonStringConstantMethodNameCount++;
            return false;
        }
        StringConstant reflectivelyInvokedMethodName = (StringConstant)getDeclaredMethodExpr.getArg(0);
        //logger.debug("Found the following method invoked reflectively: " + reflectivelyInvokedMethodName);
        if (!(getDeclaredMethodExpr.getBase() instanceof Local)) {
            System.out.println("Reflective invocation receives a non-local method name at " + methodAssignStmt);
            return false;
        }
        Local classLocal = (Local)getDeclaredMethodExpr.getBase();
        List<Unit> classDefUnits = defs.getDefsOfAt(classLocal,inStmt);
        boolean foundClassName = false;
        for (Unit classDefUnit : classDefUnits) {
            if (!(classDefUnit instanceof JAssignStmt)) {
                return false;
            }
            JAssignStmt classAssignStmt = (JAssignStmt) classDefUnit;
            if (classAssignStmt.getRightOp() instanceof InvokeExpr) {
                InvokeExpr classInvokeExpr = (InvokeExpr) classAssignStmt.getRightOp();
                if (classInvokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && classInvokeExpr.getMethod().getName().equals("forName")) {
                    if (classInvokeExpr.getArg(0) instanceof StringConstant) {
                        StringConstant classNameConst = (StringConstant) classInvokeExpr.getArg(0);
                        String fullMethodName = classNameConst.value + "." + reflectivelyInvokedMethodName.value;
                        System.out.println("\twith class: " + classNameConst.value);
                        System.out.println("\tFound reflective invocation of " + fullMethodName);
                        foundClassName = true;
                        incrementFullMethodCounts(fullMethodName);
                        fullMethodNameCount++;
                    }
                } else if (classLoaderClass != null) {
                    if (hierarchy.isClassSubclassOfIncluding(classInvokeExpr.getMethod().getDeclaringClass(), classLoaderClass)) {
                        if (classInvokeExpr.getMethod().getName().equals("loadClass")) {
                            if (classInvokeExpr.getArg(0) instanceof StringConstant) {
                                StringConstant classNameConst = (StringConstant) classInvokeExpr.getArg(0);
                                String fullMethodName = classNameConst.value + "." + reflectivelyInvokedMethodName.value;
                                System.out.println("\twith class: " + classNameConst.value);
                                System.out.println("\tFound reflective invocation of " + fullMethodName);
                                foundClassName = true;
                                incrementFullMethodCounts(fullMethodName);
                                fullMethodNameCount++;
                            }
                        }
                    }
                } else if (classInvokeExpr.getMethod().getName().equals("getClass")) {
                    if (classInvokeExpr instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr classVirtualInvokeExpr = (VirtualInvokeExpr) classInvokeExpr;
                        if (classVirtualInvokeExpr.getBase() instanceof Local) {
                            Local objLocal = (Local) classVirtualInvokeExpr.getBase();
                            for (Unit objDefUnit : defs.getDefsOfAt(objLocal, classAssignStmt)) {
                                if (objDefUnit instanceof JAssignStmt) {
                                    JAssignStmt objAssignStmt = (JAssignStmt) objDefUnit;
                                    if (objAssignStmt.getRightOp() instanceof InvokeExpr) {
                                        InvokeExpr objInvokeExpr = (InvokeExpr) objAssignStmt.getRightOp();
                                        String fullMethodName = objInvokeExpr.getMethod().getReturnType() + "." + reflectivelyInvokedMethodName.value;
                                        System.out.println("\tFound reflective invocation of " + fullMethodName);
                                        foundClassName = true;
                                        incrementFullMethodCounts(fullMethodName);
                                        fullMethodNameCount++;
                                    } else if (objAssignStmt.getRightOp() instanceof FieldRef) {
                                        FieldRef fieldRef = (FieldRef)objAssignStmt.getRightOp();
                                        String fullMethodName = fieldRef.getField().getType() + "." + reflectivelyInvokedMethodName.value;
                                        System.out.println("\tFound reflective invocation of " + fullMethodName);
                                        foundClassName = true;
                                        incrementFullMethodCounts(fullMethodName);
                                        fullMethodNameCount++;
                                    }
                                }
                            }
                        }
                        if (!foundClassName) {
                            foundClassName = true;
                            System.out.println("\tCould not find class name for the following reflectively invoked method: " + reflectivelyInvokedMethodName.value);
                            incrementPartMethodCounts(reflectivelyInvokedMethodName.value);
                            methodNameWithoutClassNameCount++;
                        }
                    }
                }
            } else if (classAssignStmt.getRightOp() instanceof ClassConstant) {
                ClassConstant classConstant = (ClassConstant)classAssignStmt.getRightOp();
                String fullMethodName = classConstant.getValue() + "." + reflectivelyInvokedMethodName.value;
                System.out.println("\tFound reflective invocation of " + fullMethodName);
                foundClassName = true;
                incrementFullMethodCounts(fullMethodName);
                fullMethodNameCount++;
            }
        }
        if (!foundClassName) {
            System.out.println("\tCould not find class name to match reflective invocation of method: " + reflectivelyInvokedMethodName);
            incrementPartMethodCounts(reflectivelyInvokedMethodName.value);
            methodNameWithoutClassNameCount++;
        }
        return true;
    }


    private void incrementFullMethodCounts(String fullMethodName) {
        Integer count = null;
        if (fullMethodCounts.containsKey(fullMethodName)) {
            count = fullMethodCounts.get(fullMethodName);
        } else {
            count = 0;
        }
        count++;
        fullMethodCounts.put(fullMethodName, count);

    }

    private void incrementPartMethodCounts(String methodNameOnly) {
        Integer count = null;
        if (partMethodCounts.containsKey(methodNameOnly)) {
            count = partMethodCounts.get(methodNameOnly);
        } else {
            count = 0;
        }
        count++;
        partMethodCounts.put(methodNameOnly, count);

    }

    public int getTotalReflectInvokeCount(){
        return totalReflectInvokeCount;
    }

}
