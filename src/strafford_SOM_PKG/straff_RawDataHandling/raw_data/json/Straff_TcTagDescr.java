package strafford_SOM_PKG.straff_RawDataHandling.raw_data.json;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import base_Utils_Objects.io.MessageObject;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base.Straff_JSONDescr;

/**
 * doesn't use json in db currently to describe data, just has string values in columns, but they follow the same format as event data
 * @author john
 *
 */
public class Straff_TcTagDescr extends Straff_JSONDescr{
	public Straff_TcTagDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {        super(_msgObj,_mapOfJson, _keysToMatchExact);    }
	@Override
	public TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpAras){return decodeJPData(jpAras);}
}//class TCTagDescr