package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.HashMap;
import java.util.Map;

/**
 * enum used to specify the type of event responsible for data
 */
public enum Straff_EvtDataType {
	Order(0), Opt(1), Link(2), Source(3);
	private int value; 
	private static Map<Integer, Straff_EvtDataType> map = new HashMap<Integer, Straff_EvtDataType>(); 
	static { for (Straff_EvtDataType enumV : Straff_EvtDataType.values()) { map.put(enumV.value, enumV);}}
	private Straff_EvtDataType(int _val){value = _val;} 
	public int getVal(){return value;}
	public static Straff_EvtDataType getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
	@Override
    public String toString() { return ""+this.name()+"("+value+")"; }	
}//enum EvtDataType

