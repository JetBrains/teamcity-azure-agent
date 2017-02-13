/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.asm.models;

/**
 * Image model.
 */
public class Image {

    private String myName;
    private String myLabel;
    private String myOs;
    private Boolean myGeneralized;

    public Image(String name,
                 String label,
                 String os,
                 boolean generalized) {
        myName = name;
        myLabel = label;
        myOs = os;
        myGeneralized = generalized;
    }

    public String getName() {
        return myName;
    }

    public String getLabel() {
        return myLabel;
    }

    public String getOs() {
        return myOs;
    }

    public Boolean getGeneralized() {
        return myGeneralized;
    }
}
