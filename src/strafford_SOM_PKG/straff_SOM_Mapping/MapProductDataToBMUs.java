package strafford_SOM_PKG.straff_SOM_Mapping;

import java.util.ArrayList;
import java.util.TreeMap;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_utils.*;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.ProductExample;

//maps products to all map nodes, not just bmu
public class MapProductDataToBMUs extends MapDataToBMUs{
	ProductExample[] exs;

	public MapProductDataToBMUs(SOMMapManager _mapMgr, int _stProdIDX, int _endProdIDX, ProductExample[] _exs, int _thdIDX, boolean _useChiSqDist) {
		super(_mapMgr, _stProdIDX,  _endProdIDX,  _thdIDX, "Product", _useChiSqDist);
		exs = _exs;		//make sure these are cast appropriately
	}	
	@Override
	protected boolean mapAllDataToBMUs() {
//		//for every product example find closest map node for both shared and all ftrs being compared
//		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting "+dataType+" data to BMU mapping using " + (endIdx-stIdx) + " of " + exs.length+" examples ["+stIdx+":"+endIdx+"] with " + ftrTypeDesc + " Features and both including and excluding unshared features in distance.", MsgCodes.info5);
//		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist;
//		if (useChiSqDist) {	
//			for (int i=stIdx;i<endIdx;++i) {
//				//perform excluded zero ftrs last so that this is what is set to be product's bmu
//				mapNodesByDist = exs[i].findBMUFromNodes_ChiSq(MapNodes, curMapFtrType); 
//				exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);  
//				mapNodesByDist = exs[i].findBMUFromNodes_ChiSq_Excl(MapNodes, curMapFtrType); 
//				exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);
//				incrProgress(i);
//			}
//		} else {							
//			for (int i=stIdx;i<endIdx;++i) {
//				//perform excluded zero ftrs last so that this is what is set to be product's bmu
//				mapNodesByDist = exs[i].findBMUFromNodes(MapNodes,  curMapFtrType); 
//				exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);
//				mapNodesByDist = exs[i].findBMUFromNodes_Excl(MapNodes,  curMapFtrType); 
//				exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);
//				incrProgress(i);
//			}
//		}					
//		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished "+dataType+" data to BMU mapping", MsgCodes.info5);
		
		//for every product example find closest map node for both shared and all ftrs being compared
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Starting "+dataType+" data to BMU mapping using " + (endIdx-stIdx) + " of " + exs.length+" examples ["+stIdx+":"+endIdx+"] with " + ftrTypeDesc + " Features and both including and excluding unshared features in distance.", MsgCodes.info5);
		TreeMap<Double, ArrayList<SOMMapNode>> mapNodesByDist;
		if (useChiSqDist) {	
			for (int i=stIdx;i<endIdx;++i) {
				//perform excluded zero ftrs last so that this is what is set to be product's bmu
				mapNodesByDist = exs[i].findBMUFromFtrNodes_ChiSq_Excl(MapNodesByFtr, curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + exs[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);}
				mapNodesByDist = exs[i].findBMUFromFtrNodes_ChiSq(MapNodesByFtr, curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + exs[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist); }
				incrProgress(i);
			}
		} else {							
			for (int i=stIdx;i<endIdx;++i) {
				//perform excluded zero ftrs last so that this is what is set to be product's bmu
				mapNodesByDist = exs[i].findBMUFromFtrNodes_Excl(MapNodesByFtr,  curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + exs[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {exs[i].setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);}
				mapNodesByDist = exs[i].findBMUFromFtrNodes(MapNodesByFtr,  curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + exs[i].OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {exs[i].setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);}
				incrProgress(i);
			}
		}					
		msgObj.dispMessage("mapTestDataToBMUs", "Run Thread : " +thdIDX, "Finished "+dataType+" data to BMU mapping", MsgCodes.info5);
		
		return true;
	}		
}//mapTestToBMUs
	