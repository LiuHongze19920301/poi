/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.ss.formula.functions;

import static org.junit.Assert.assertEquals;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.formula.eval.EvaluationException;
import org.junit.Test;

public class TestMultiOperandNumericFunction {

    @Test
    public void testSettings() {
        MultiOperandNumericFunction fun = new MultiOperandNumericFunction(true, true) {
            @Override
            protected double evaluate(double[] values) throws EvaluationException {
                return 0;
            }
            
        };
        assertEquals(SpreadsheetVersion.EXCEL2007.getMaxFunctionArgs(), fun.getMaxNumOperands());
    }
}
