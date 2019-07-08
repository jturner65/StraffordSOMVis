package strafford_SOM_PKG.straff_RawDataHandling.raw_data.json;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import base_Utils_Objects.io.MessageObject;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base.Straff_JSONDescr;

/**
 * prospect records hold data with specific format, so needs a different convert method
 * @author john
 *
 */
public class Straff_ProspectDescr extends Straff_JSONDescr{
	public Straff_ProspectDescr(MessageObject _msgObj,Map<String, Object> _mapOfJson, String[] _keysToMatchExact) {super(_msgObj,_mapOfJson,_keysToMatchExact);}

	@Override
	public TreeMap<Integer, ArrayList<Integer>> convertToJpgJps(String jpRawDataStr) {    
		//NOT READING PROSPECT RECORD FOR JP/JPG anymore so this should be ignored - this is replaced by using source data
		return new TreeMap<Integer, ArrayList<Integer>>();
	}    
}//class prospectDescr