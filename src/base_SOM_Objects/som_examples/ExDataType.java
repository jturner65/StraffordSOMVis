package base_SOM_Objects.som_examples;

import java.util.*;

/**
 * enum used to specify each kind of example data point, primarily for visualization purposes
 * @author john turner 
 */
public enum ExDataType {
	Training(0), Testing(1), Validation(2), Product(3), MapNode(4), MouseOver(5);
	private int value; 
	private String[] _typeExplanation = new String[] {
			"Training Data (Used to train the SOM)", 
			"Testing Data (Held-out training data used to investigate training)",
			"Validation Data (Data to be clustered on map)",
			"Product Data (Product examples to be assigned to clusters)",
			"Map Node (Represents a node on the SOM)",
			"Mouse Over (Data query at mouse location)"};
	private static String[] _typeName = new String[] {"Training","Testing","Validation","Product","MapNode","MouseOver"};
	public static String[] getListOfTypes() {return _typeName;}
	private static Map<Integer, ExDataType> map = new HashMap<Integer, ExDataType>(); 
	static { for (ExDataType enumV : ExDataType.values()) { map.put(enumV.value, enumV);}}
	private ExDataType(int _val){value = _val;} 
	public int getVal(){return value;}
	public static ExDataType getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
	public String getName() {return _typeName[value];}
	@Override
    public String toString() { return ""+value + ":"+_typeExplanation[value]; }	
}//enum ExDataType


