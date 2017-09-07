/*
 * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arialyy.compiler;

import com.arialyy.annotations.Download;
import com.arialyy.annotations.Upload;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;

/**
 * Created by lyy on 2017/9/6.
 * 任务事件代理文件
 *
 * <pre>
 *   <code>
 * package com.arialyy.simple.download;
 *
 * import com.arialyy.aria.core.download.DownloadTask;
 * import com.arialyy.aria.core.scheduler.AbsSchedulerListener;
 *
 * public final class SingleTaskActivity$$DownloadListenerProxy extends
 * AbsSchedulerListener<DownloadTask> {
 * private SingleTaskActivity obj;
 *
 *    public void onPre(final DownloadTask task) {
 *      obj.onPre((DownloadTask)task);
 *    }
 *
 *    public void onTaskStart(final DownloadTask task) {
 *      obj.onStart((DownloadTask)task);
 *    }
 *
 *    public void setListener(final Object obj) {
 *      this.obj = (SingleTaskActivity)obj;
 *    }
 * }
 *   </code>
 * </pre>
 */
final class EventProxyFiler {

  private Filer mFiler;
  private ParamObtainUtil mPbUtil;

  EventProxyFiler(Filer filer, ParamObtainUtil pbUtil) {
    mFiler = filer;
    mPbUtil = pbUtil;
  }

  /**
   * 创建任务事件代理文件
   */
  void createEventProxyFile() throws IOException {
    Map<String, ProxyClassParam> map = mPbUtil.getMethodParams();
    Set<String> keys = mPbUtil.getMethodParams().keySet();
    for (String key : keys) {
      //if (key.equals(
      //    "com.arialyy.simple.download.SingleTaskActivity$$DownloadGroupListenerProxy")) {
      //
      //  ProxyClassParam entity = mPbUtil.getMethodParams().get(key);
      //  JavaFile jf = JavaFile.builder(entity.packageName, createProxyClass(entity)).build();
      //  createFile(jf);
      //}

      ProxyClassParam entity = mPbUtil.getMethodParams().get(key);
      JavaFile jf = JavaFile.builder(entity.packageName, createProxyClass(entity)).build();
      createFile(jf);

    }
  }

  /**
   * 创建代理方法
   *
   * @param taskEnum 任务类型枚举{@link TaskEnum}
   * @param annotation {@link Download}、{@link Upload}
   * @param methodName 被代理类注解的方法名
   */
  private MethodSpec createProxyMethod(TaskEnum taskEnum, Class<? extends Annotation> annotation,
      String methodName) {
    ClassName task = ClassName.get(taskEnum.getPkg(), taskEnum.getClassName());

    ParameterSpec taskParam =
        ParameterSpec.builder(task, "task").addModifiers(Modifier.FINAL).build();

    String callCode;
    if (taskEnum == TaskEnum.DOWNLOAD_GROUP_SUB) {
      callCode = "task, subTask";
    } else {
      callCode = "task";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Set<String> keys = keyMapping.get(\"").append(methodName).append("\");\n");
    sb.append("if (keys != null) {\n\tif (keys.contains(task.getKey())) {\n")
        .append("\t\tobj.")
        .append(methodName)
        .append("((")
        .append(taskEnum.getClassName())
        .append(")")
        .append(callCode)
        .append(");\n\t}\n} else {\n")
        .append("\tobj.")
        .append(methodName)
        .append("((")
        .append(taskEnum.getClassName())
        .append(")")
        .append(callCode)
        .append(");\n}\n");

    MethodSpec.Builder builder = MethodSpec.methodBuilder(annotation.getSimpleName())
        .addModifiers(Modifier.PUBLIC)
        .returns(void.class)
        .addParameter(taskParam)
        .addAnnotation(Override.class)
        .addCode(sb.toString());

    //任务组接口
    if (taskEnum == TaskEnum.DOWNLOAD_GROUP_SUB) {
      ClassName subTask = ClassName.get(TaskEnum.DOWNLOAD.pkg, TaskEnum.DOWNLOAD.className);
      ParameterSpec subTaskParam =
          ParameterSpec.builder(subTask, "subTask").addModifiers(Modifier.FINAL).build();

      builder.addParameter(subTaskParam);
    }

    //PrintLog.getInstance().info(builder.build().toString());

    return builder.build();
  }

  /**
   * 创建代理类
   */
  private TypeSpec createProxyClass(ProxyClassParam entity) {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(entity.proxyClassName).addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    //添加被代理的类的字段
    ClassName obj = ClassName.get(entity.packageName, entity.className);
    FieldSpec observerField = FieldSpec.builder(obj, "obj").addModifiers(Modifier.PRIVATE).build();
    builder.addField(observerField);

    //添加url映射表
    FieldSpec mappingField = FieldSpec.builder(
        ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class),
            ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class))),
        "keyMapping").addModifiers(Modifier.PRIVATE).initializer("new $T()", HashMap.class).build();
    builder.addField(mappingField);

    //添加注解方法
    for (TaskEnum te : entity.methods.keySet()) {
      Map<Class<? extends Annotation>, String> temp = entity.methods.get(te);
      if (temp != null) {
        for (Class<? extends Annotation> annotation : temp.keySet()) {
          MethodSpec method = createProxyMethod(te, annotation, temp.get(annotation));
          builder.addMethod(method);
        }
      }
    }

    //增加构造函数
    CodeBlock.Builder cb = CodeBlock.builder();
    cb.add("Set<String> set = null;\n");
    for (String methodName : entity.keyMappings.keySet()) {
      //PrintLog.getInstance().info("methodName ====> " +  methodName);
      Set<String> keys = entity.keyMappings.get(methodName);
      if (keys == null || keys.size() == 0) continue;
      StringBuilder sb = new StringBuilder();
      sb.append("set = new $T();\n");
      for (String key : keys) {
        if (key.isEmpty()) continue;
        sb.append("set.add(\"").append(key).append("\");\n");
      }

      sb.append("keyMapping.put(\"").append(methodName).append("\", ").append("set);\n");
      cb.add(sb.toString(), ClassName.get(HashSet.class));
    }
    MethodSpec structure =
        MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).addCode(cb.build()).build();
    builder.addMethod(structure);

    //添加设置代理的类
    ParameterSpec parameterSpec =
        ParameterSpec.builder(Object.class, "obj").addModifiers(Modifier.FINAL).build();
    MethodSpec listener = MethodSpec.methodBuilder(ProxyConstance.SET_LISTENER)
        .addModifiers(Modifier.PUBLIC)
        .returns(void.class)
        .addParameter(parameterSpec)
        .addAnnotation(Override.class)
        .addCode("this.obj = (" + entity.className + ")obj;\n")
        .build();
    builder.addJavadoc("该文件为Aria自动生成的代理文件，请不要修改该文件的任何代码！\n");

    //创建父类参数
    ClassName superClass = ClassName.get("com.arialyy.aria.core.scheduler", "AbsSchedulerListener");
    //主任务泛型参数
    ClassName taskTypeVariable =
        ClassName.get(entity.mainTaskEnum.pkg, entity.mainTaskEnum.className);
    //子任务泛型参数
    ClassName subTaskTypeVariable =
        ClassName.get(entity.subTaskEnum.pkg, entity.subTaskEnum.className);

    builder.superclass(
        ParameterizedTypeName.get(superClass, taskTypeVariable, subTaskTypeVariable));
    builder.addMethod(listener);
    return builder.build();
  }

  private void createFile(JavaFile jf) throws IOException {
    if (ProxyConstance.DEBUG) {
      // 如果需要在控制台打印生成的文件，则去掉下面的注释
      jf.writeTo(System.out);
    } else {
      jf.writeTo(mFiler);
    }
  }
}
