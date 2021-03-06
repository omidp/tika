/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.mat;

//JDK imports
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.apache.tika.io.IOUtils;

//JMatIO imports
import com.jmatio.io.MatFileHeader;
import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLStructure;
import com.jmatio.types.MLCell;
import com.jmatio.common.MatDataTypes;


public class MatParser extends AbstractParser {
    
    public static final String MATLAB_MIME_TYPE = 
    "application/x-matlab-data";  

    private final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MediaType.application("x-matlab-data"));

    public Set<MediaType> getSupportedTypes(ParseContext context){
        return SUPPORTED_TYPES;
        }

    public void parse(InputStream stream, ContentHandler handler,
        Metadata metadata, ParseContext context) throws IOException,
        SAXException, TikaException {
 				
        //Set MIME type as metadata
        metadata.set(Metadata.CONTENT_TYPE, MATLAB_MIME_TYPE);
         	
        try {
        	
            //Input file stream
            TikaInputStream tis = TikaInputStream.get(stream);
	        						
            //Extract information from header file
            MatFileReader mfr = new MatFileReader(tis.getFile()); //input .mat file
            MatFileHeader hdr = mfr.getMatFileHeader(); //.mat header information

            String stringToSplit = hdr.getDescription(); //break header information into its parts
            String[] parts = stringToSplit.split(",");
				
            // Ex .mat header "MATLAB 5.0 MAT-file, Platform: MACI64, Created on: Sun Mar  2 23:41:57 2014"
            if (parts[2].contains("Created")) {
                int lastIndex1 = parts[2].lastIndexOf("Created on:");
                String dateCreated = parts[2].substring(lastIndex1 + 11).trim();
                metadata.set("createdOn", dateCreated);
                }
    			
            if (parts[1].contains("Platform")) {
                int lastIndex2 = parts[1].lastIndexOf("Platform:");
                String platform = parts[1].substring(lastIndex2 + 9).trim();
                metadata.set("platform" , platform);
                }
    				
            if (parts[0].contains("MATLAB")) {	
                metadata.set("fileType", parts[0]);
                }
    			
            //Get endian indicator from header file
            String endianBytes = new String(hdr.getEndianIndicator()); //retrieve endian bytes and convert to string
            String endianCode = String.valueOf(endianBytes.toCharArray()); //convert bytes to characters to string
            metadata.set("endian", endianCode);
	
            //Text output	
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);          		
            xhtml.startDocument();
           		
            //Get array names, size, and data types
            Map<String, MLArray> data = mfr.getContent();
            Set<String> vars = data.keySet();
        		
            //Loop through each variable 
            for (Iterator<String> var = vars.iterator(); var.hasNext();) {
                String varName = var.next();
                MLArray varData = data.get(varName);
                   
                xhtml.characters(varName);
                xhtml.characters(":");
                xhtml.characters(String.valueOf(varData));
                xhtml.newline();
                   
                //if the variable is a structure, extract variable info from structure
                if (varData.isStruct()){
                    MLStructure mlStructure = (MLStructure) mfr.getMLArray(varName);	
                    Collection<MLArray> list = mlStructure.getAllFields();
				   
                    for (MLArray element : list){
                        xhtml.characters("  ");
                        xhtml.characters(String.valueOf(element));
                        xhtml.newline();
						
                        //if there is an imbedded structure, extract variable info.						
                        if (element.isStruct()){																		 
                            String nest = element.contentToString();
                            xhtml.characters("      ");
                            xhtml.characters(nest);   
                            xhtml.newline();
                            }
                        }
                    }		
            } 

            xhtml.endDocument();		
                		
            } catch (IOException e) {
                throw new TikaException("matparser error", e);
                } 
        }        
}