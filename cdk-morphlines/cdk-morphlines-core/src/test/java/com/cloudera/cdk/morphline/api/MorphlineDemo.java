/*
 * Copyright 2013 Cloudera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.cdk.morphline.api;

import java.io.*;
import com.cloudera.cdk.morphline.api.*;
import com.cloudera.cdk.morphline.base.*;
import com.cloudera.cdk.morphline.base.Compiler;

/** Sample program that illustrates how to use the API to embed and execute a morphline in a host system */
public class MorphlineDemo {
  
  /** Usage: java ... <morphline.conf> <dataFile1> ... <dataFileN> */
  public static void main(String[] args) throws Exception {
    File morphlineFile = new File(args[0]);
    String morphlineId = null;
    MorphlineContext morphlineContext = new MorphlineContext.Builder().build();
    Command morphline = new Compiler().compile(morphlineFile, morphlineId, morphlineContext, null);    
    for (int i = 1; i < args.length; i++) {
      Record record = new Record();
      InputStream in = new BufferedInputStream(new FileInputStream(new File(args[i])));
      record.put(Fields.ATTACHMENT_BODY, in);      
      try {
        Notifications.notifyStartSession(morphline);
        boolean success = morphline.process(record);
        if (!success) {
          System.out.println("Morphline failed to process record: " + record);
        }
      } catch (RuntimeException t) {
        morphlineContext.getExceptionHandler().handleException(t, record);
      } finally {
        in.close();
      }
    }
  }
}
