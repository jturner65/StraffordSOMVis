package SOM_Strafford_PKG;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import base_SOM_Objects.*;
import base_Utils_Objects.MsgCodes;

//maps products to all map nodes, not just bmu
public class mapProductDataToBMUs extends mapDataToBMUs{
	ProductExample[] exs;

	public mapProductDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, ProductExample[] _exs, int _thdIDX, boolean _useChiSqDist) {
		super(_mapMgr, _stProdIDX,  _endProdIDX,  _thdIDX, "Product", _useChiSqDist);
		exs = _exs;		//make sure these are cast appropriately
	}	
	@Override
	protected boolean mapAllDataToBMUs() {
		//for every example find closest map node
		//the function call at the end is ignored by product examples
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting "+dataType+" data to BMU mapping using " + (endIdx-stIdx) + " of " + exs.length+" examples ["+stIdx+":"+endIdx+"] with " + ftrTypeDesc + " Features and both including and excluding unshared features in distance.", MsgCodes.info5);
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist;
		if (useChiSqDist) {	
			for (int i=stIdx;i<endIdx;++i) {
				mapNodesByDist = exs[i].findBMUFromNodes_ChiSq_Excl(MapNodes, curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);
				mapNodesByDist = exs[i].findBMUFromNodes_ChiSq(MapNodes, curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);  
			}
		} else {							
			for (int i=stIdx;i<endIdx;++i) {
				mapNodesByDist = exs[i].findBMUFromNodes_Excl(MapNodes,  curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);
				mapNodesByDist = exs[i].findBMUFromNodes(MapNodes,  curMapFtrType); 
				exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);
			}
		}					
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished "+dataType+" data to BMU mapping", MsgCodes.info5);
		
		return true;
	}		
}//mapTestToBMUs
	

class custCSVDataLoader extends SOMExCSVDataLoader{

	public custCSVDataLoader(SOMMapManager _mapMgr, int _thdIDX, String _fileName, String _yStr, String _nStr,
			ConcurrentSkipListMap<String, SOMExample> _mapToAddTo) {	
		super(_mapMgr, _thdIDX, _fileName, _yStr, _nStr, _mapToAddTo);type="custCSVDataLoader";
	}
	@Override
	protected SOMExample buildExample(String oid, String str) {return new custProspectExample(mapMgr, oid, str);}
	
}//custCSVDataLoader

class prscpctCSVDataLoader extends SOMExCSVDataLoader{
	public prscpctCSVDataLoader(SOMMapManager _mapMgr, int _thdIDX, String _fileName, String _yStr, String _nStr,
			ConcurrentSkipListMap<String, SOMExample> _mapToAddTo) {	
		super(_mapMgr, _thdIDX, _fileName, _yStr, _nStr, _mapToAddTo);type="prscpctCSVDataLoader";
	}
	@Override
	protected SOMExample buildExample(String oid, String str) {return new trueProspectExample(mapMgr, oid, str);}
}//class prscpctCSVDataLoader