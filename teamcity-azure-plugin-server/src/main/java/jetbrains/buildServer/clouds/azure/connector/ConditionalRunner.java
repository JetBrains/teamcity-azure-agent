package jetbrains.buildServer.clouds.azure.connector;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

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
      try {
        if (item.canExecute()){
          try {
            item.execute();
          } catch (Exception ex){
            LOG.warn(ex.toString());
          }
          myItems.remove(item);
        }
      } catch (Exception e) {
        myItems.remove(item);
      }
    }
  }

  public static void addConditional(Conditional conditional){
    myItems.add(conditional);
  }

  public static interface Conditional{

    boolean canExecute() throws Exception;

    void execute();
  }
}
