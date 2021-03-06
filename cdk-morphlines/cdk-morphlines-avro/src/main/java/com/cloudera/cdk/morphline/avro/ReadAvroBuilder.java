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
package com.cloudera.cdk.morphline.avro;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;

import com.cloudera.cdk.morphline.api.Command;
import com.cloudera.cdk.morphline.api.CommandBuilder;
import com.cloudera.cdk.morphline.api.MorphlineCompilationException;
import com.cloudera.cdk.morphline.api.MorphlineContext;
import com.cloudera.cdk.morphline.api.Record;
import com.cloudera.cdk.morphline.avro.ReadAvroContainerBuilder.ReadAvroContainer;
import com.cloudera.cdk.morphline.base.Fields;
import com.google.common.base.Preconditions;
import com.typesafe.config.Config;


/**
 * Command that parses an InputStream that contains Avro data; for each Avro datum, the command
 * emits a morphline record containing the datum as an attachment in {@link Fields#ATTACHMENT_BODY}.
 * 
 * The Avro schema that was used to write the Avro data must be explicitly supplied. Optionally, the
 * Avro schema that shall be used for reading can be supplied as well.
 */
public final class ReadAvroBuilder implements CommandBuilder {

  /** The MIME type identifier that will be filled into output records */
  public static final String AVRO_MEMORY_MIME_TYPE = "avro/java+memory";

  @Override
  public Collection<String> getNames() {
    return Collections.singletonList("readAvro");
  }
  
  @Override
  public Command build(Config config, Command parent, Command child, MorphlineContext context) {
    return new ReadAvro(config, parent, child, context);
  }
  
  
  ///////////////////////////////////////////////////////////////////////////////
  // Nested classes:
  ///////////////////////////////////////////////////////////////////////////////
  final static class ReadAvro extends ReadAvroContainer {

    protected final Schema writerSchema;
    private final boolean isJson;
    
    public ReadAvro(Config config, Command parent, Command child, MorphlineContext context) {
      super(config, parent, child, context);
      
      String schemaString = getConfigs().getString(config, "writerSchemaString", null);
      if (schemaString != null) {
        this.writerSchema = new Parser().parse(schemaString);
      } else {        
        String schemaFile = getConfigs().getString(config, "writerSchemaFile", null);
        if (schemaFile != null) {
          try { 
            this.writerSchema = new Parser().parse(new File(schemaFile));
          } catch (IOException e) {
            throw new MorphlineCompilationException("Cannot parse external Avro writer schema file: " + schemaFile, config, e);
          }
        } else {
          this.writerSchema = null;
        }
      }
      
      this.isJson = getConfigs().getBoolean(config, "isJson", false);
      validateArguments();
    }
    
    @Override
    protected void validateArguments() {
      super.validateArguments();
      if (writerSchema == null) {
        throw new MorphlineCompilationException(
            "You must specify an external Avro writer schema because this is required to read containerless Avro", getConfig());
      }
    }
    
    private boolean isJSON() {
      return isJson;
    }

    @Override
    protected boolean doProcess(Record inputRecord, InputStream in) throws IOException {
      Preconditions.checkNotNull(writerSchema);  
      Schema readSchema = readerSchema != null ? readerSchema : writerSchema;
      DatumReader<GenericContainer> datumReader = new GenericDatumReader<GenericContainer>(writerSchema, readSchema);
      
      Decoder decoder;
      if (isJSON()) {
        decoder = DecoderFactory.get().jsonDecoder(writerSchema, in);
      } else {
        decoder = DecoderFactory.get().binaryDecoder(in, null);
      }
            
      try {
        while (true) {
          GenericContainer datum = datumReader.read(null, decoder);
          if (!extract(datum, inputRecord)) {
            return false;
          }
        }
      } catch (EOFException e) { 
        ; // ignore
      } finally {
        in.close();
      }
      return true;
    }
    
  }
  
}
