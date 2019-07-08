package strafford_SOM_PKG.straff_RawDataHandling.raw_data.json;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import base_Utils_Objects.io.MessageObject;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base.Straff_JSONDescr;

public class Straff_JpDescr extends Straff_JSONDescr{
	public Straff_JpDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_msgObj,_mapOfJson, _keysToMatchExact);}
	@Override
	public TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras) {    return null;}//doesn't use this

}//class jobPracDescr