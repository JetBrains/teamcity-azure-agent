/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.base;

import java.util.Map;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;

/**
 * @author Sergey.Pak
 *         Date: 10/27/2014
 *         Time: 3:57 PM
 */
public class FakeModel {
  private static final FakeModel instance = new FakeModel();

  public static FakeModel instance(){
    return instance;
  }

  public Map<String, AbstractInstance> getInstances(){
    return null;
  }

}
