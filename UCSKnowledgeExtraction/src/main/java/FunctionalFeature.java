import java.util.ArrayList;
import java.util.List;

public class FunctionalFeature {
    private String actor;
    private String action;
    private String result;
    private String object;
    private String n1;
    private Integer ID;
    private String prCond ="";
    public boolean condOrLoop = false;
    private List<Integer> CauseID = new ArrayList<>();
    private List<Integer> effectStepID = new ArrayList<>();
    private Integer stepID;

    public FunctionalFeature(String actor, String action, String result, String object, int id, int step) {
        this.actor = actor;
        this.action = action;
        this.result = result;
        this.object = object;
        this.ID = id;
        this.stepID = step;
    }

    public void setActor(String a){
        this.actor=a;
    }

    public void setAction(String a){
        this.action=a;
    }

    public void setResult(String r){
        this.result=r;
    }

    public void setObject(String o){
        this.object=o;
    }

    public void setN1(String n){
        this.n1=n;
    }

    public void setID(Integer ID) {
        this.ID = ID;
    }

    public void setPrCond(String prCond) {
        this.prCond = prCond;
    }

    public void setCauseID(List<Integer> causeID) {
        CauseID = causeID;
    }

    public Integer getID() {
        return ID;
    }

    public Integer getStepID() {
        return stepID;
    }

    public String getPrCond() {
        return prCond;
    }

    public List<Integer> getCauseID() {
        return CauseID;
    }

    public List<Integer> getEffectStepID() {
        return effectStepID;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getResult() {
        return result;
    }

    public String getObject() {
        return object;
    }

    public String getN1() {
        return n1;
    }

    public void addToCauseID (int x){
        this.getCauseID().add(x);
    }

    public void addToEffectStepID (int x){ this.getEffectStepID().add(x); }

    public void addPreviousStepIds(List<FunctionalFeature> ffList) {
        for (FunctionalFeature ff : ffList) {
            if (ff.getStepID()==this.stepID-1) {
                if (!ff.condOrLoop)
                    this.addToCauseID(ff.getID());
            }
        }
    }

    public static FunctionalFeature getFF (List<FunctionalFeature> ffList, int id){
        for (FunctionalFeature ff : ffList) {
            if (ff.getID()==id){
                return ff;
            }
        }
        return null;
    }
    public static List<Integer> getFfsByStep (List<FunctionalFeature> ffList, int step){
        List<Integer> ffsReturn = new ArrayList<>();
        for (FunctionalFeature ff : ffList) {
            if (ff.getStepID()==step){
                ffsReturn.add(ff.getID());
            }
        }
        return ffsReturn;
    }

}

