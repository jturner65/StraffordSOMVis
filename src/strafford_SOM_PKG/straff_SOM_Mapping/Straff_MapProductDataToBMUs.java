package strafford_SOM_PKG.straff_SOM_Mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import base_SOM_Objects.som_utils.*;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;

/**
 * this will map each product's synthesized ftr vector to all map nodes, by ftr calc; it will also set each product's segment based on training data orders
 * The product segment information is most relevant - this is the set of all map nodes that are bmus for training examples with specified product purchases
 * @author john
 */
public class Straff_MapProductDataToBMUs extends SOM_MapDataToBMUs{
	ProductExample[] exs;

	public Straff_MapProductDataToBMUs(SOM_MapManager _mapMgr, int _stProdIDX, int _endProdIDX, ProductExample[] _exs, int _thdIDX, boolean _useChiSqDist) {
		super(_mapMgr, _stProdIDX,  _endProdIDX,  _thdIDX, "Product", _useChiSqDist);
		exs = _exs;		//make sure these are cast appropriately
	}	

	/**
	 * map to bmus and segments
	 */
	//NOTE only 1 example will be accessed per thread, so no possible concurrency issues will be caused by this code.  however, multiple mapnodes may be accessed
	@Override
	protected boolean mapAllDataToBMUs() {
		//we want to map products to bmus
		//for every product example find closest map node for both shared and all ftrs being compared
		msgObj.dispMessage("MapProductDataToBMUs", "Run Thread : " +thdIDX, "Starting "+dataType+" data to BMU mapping using " + (endIdx-stIdx) + " of " + exs.length+" examples ["+stIdx+":"+endIdx+"] with " + ftrTypeDesc + " Features and both including and excluding unshared features in distance.", MsgCodes.info5);
		TreeMap<Double, ArrayList<SOM_MapNode>> mapNodesByDist;
		ProductExample ex;
		
		if (useChiSqDist) {	
			for (int i=stIdx;i<endIdx;++i) {
				ex = exs[i];
				//perform excluded zero ftrs last so that this is what is set to be product's bmu
				mapNodesByDist = ex.findBMUFromFtrNodes(MapNodesByFtr,ex::getSqDistFromFtrType_ChiSq, curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("MapProductDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + ex.OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {ex.setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist); }
				mapNodesByDist = ex.findBMUFromFtrNodes(MapNodesByFtr,ex::getSqDistFromFtrType_ChiSq_Exclude, curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("MapProductDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + ex.OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {ex.setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);}
				//build example map of order-based map node probabilities and segments
				ex.setSegmentsAndProbsFromAllMapNodes(Class_Segments, Category_Segments);				
				incrProgress(i);
			}
		} else {							
			for (int i=stIdx;i<endIdx;++i) {
				ex = exs[i];
				mapNodesByDist = ex.findBMUFromFtrNodes(MapNodesByFtr,ex::getSqDistFromFtrType, curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("MapProductDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + ex.OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {ex.setMapNodesStruct(ProductExample.AllFtrsIDX, mapNodesByDist);}
				//perform excluded zero ftrs last so that this is what is set to be product's bmu
				mapNodesByDist = ex.findBMUFromFtrNodes(MapNodesByFtr,ex::getSqDistFromFtrType_Exclude, curMapFtrType); 
				if(mapNodesByDist == null) {msgObj.dispMessage("MapProductDataToBMUs", "Run Thread : " +thdIDX, "ERROR!!! Product " + ex.OID + " does not have any features that map to Map Nodes in SOM!", MsgCodes.error5);}
				else {ex.setMapNodesStruct(ProductExample.SharedFtrsIDX, mapNodesByDist);}
				//build example map of order-based map node probabilities and segments
				ex.setSegmentsAndProbsFromAllMapNodes(Class_Segments, Category_Segments);		
				incrProgress(i);
			}
		}					
		msgObj.dispMessage("MapProductDataToBMUs", "Run Thread : " +thdIDX, "Finished "+dataType+" data to BMU mapping", MsgCodes.info5);
		
		return true;
	}		
	
	
}//mapTestToBMUs
	