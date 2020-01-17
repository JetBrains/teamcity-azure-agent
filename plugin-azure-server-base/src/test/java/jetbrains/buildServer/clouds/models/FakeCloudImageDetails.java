/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.models;

import com.google.gson.annotations.SerializedName;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;

/**
 * @author Dmitry.Tretyakov
 *         Date: 3/2/2016
 *         Time: 2:29 PM
 */
public class FakeCloudImageDetails implements CloudImagePasswordDetails {
    @SerializedName("data")
    private final String myData;
    private String myPassword = null;

    public FakeCloudImageDetails(final String data) {
        myData = data;
    }

    @Override
    public CloneBehaviour getBehaviour() {
        return null;
    }

    @Override
    public int getMaxInstances() {
        return 0;
    }

    @Override
    public String getSourceId() {
        return "name";
    }

    @Override
    public String getPassword() {
        return myPassword;
    }

    @Override
    public void setPassword(String password) {
        myPassword = password;
    }

    public String getData() {
        return myData;
    }
}
