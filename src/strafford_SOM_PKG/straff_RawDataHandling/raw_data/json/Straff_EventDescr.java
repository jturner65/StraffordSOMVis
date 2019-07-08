package strafford_SOM_PKG.straff_RawDataHandling.raw_data.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import base_Utils_Objects.io.MessageObject;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base.Straff_JSONDescr;

public class Straff_EventDescr extends Straff_JSONDescr{
	public HashMap<Integer, ArrayList<Integer>> jpMap;
	public Straff_EventDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_msgObj,_mapOfJson,_keysToMatchExact);}
	@Override
	public TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras) {return decodeJPData(jpAras);}
}//class EventDescr
