/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.azure.connector;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 8/14/2014
 *         Time: 4:50 PM
 */
public class ConditionalRunner implements Runnable {
  private static final Logger LOG = Logger.getInstance(ConditionalRunner.class.getName());

  private static final List<Conditional> myItems = new CopyOnWriteArrayList<Conditional>();


  public void run() {
    for (Conditional item : myItems) {
      boolean remove = false;
      try {
        if (item.canExecute()){
          remove = item.execute();
          LOG.info(String.format("Executing %s. Result: %b", item.getName(), remove));
        }
      } catch (Exception e) {
        remove = true;
        LOG.warn(e.toString(), e);
      } finally {
        if (remove)
          myItems.remove(item);
      }
    }
  }

  public static void addConditional(Conditional conditional){
    myItems.add(conditional);
    LOG.info(String.format("Added conditional '%s'", conditional.getName()));
  }

  public static interface Conditional{

    @NotNull
    String getName();

    boolean canExecute() throws Exception;

    boolean execute() throws Exception;
  }
}
