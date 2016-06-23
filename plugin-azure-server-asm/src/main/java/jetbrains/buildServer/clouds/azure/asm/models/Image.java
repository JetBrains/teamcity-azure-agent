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
