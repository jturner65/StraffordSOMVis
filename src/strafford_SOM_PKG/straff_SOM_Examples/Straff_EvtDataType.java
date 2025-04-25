package strafford_SOM_PKG.straff_SOM_Examples;

import java.util.HashMap;
import java.util.Map;

/**
 * enum used to specify the type of event responsible for data
 */
public enum Straff_EvtDataType {
	Order, Opt, Link, Source;
	private static Map<Integer, Straff_EvtDataType> map = new HashMap<Integer, Straff_EvtDataType>(); 
	static { for (Straff_EvtDataType enumV : Straff_EvtDataType.values()) { map.put(enumV.ordinal(), enumV);}}
	public int getVal(){return ordinal();}
	public static Straff_EvtDataType getEnumByIndex(int idx){return map.get(idx);}
	public static Straff_EvtDataType getEnumFromValue(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
	@Override
    public String toString() { return ""+this.name()+"("+ordinal()+")"; }	
}//enum EvtDataType

