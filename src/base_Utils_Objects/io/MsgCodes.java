package base_Utils_Objects.io;

import java.util.*;

public //enum delineates each kind of message to be displayed - various information and error codes 
enum MsgCodes{
	info1(0),info2(1),info3(2), info4(3), info5(4),
	warning1(5),warning2(6),warning3(7),warning4(8),warning5(9),
	error1(10),error2(11),error3(12),error4(13),error5(14);
	
	private int value; 
	private static Map<Integer, MsgCodes> map = new HashMap<Integer, MsgCodes>(); 
	static { for (MsgCodes enumV : MsgCodes.values()) { map.put(enumV.value, enumV);}}
	private MsgCodes(int _val){value = _val;} 
	public int getVal(){return value;}
	public static MsgCodes getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
	@Override
    public String toString() { return ""+value; }	
}