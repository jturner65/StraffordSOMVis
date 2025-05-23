package strafford_SOM_PKG.straff_SOM_Segments;

import java.util.TreeMap;

import base_SOM_Objects.som_examples.base.SOM_Example;
import base_SOM_Objects.som_examples.enums.SOM_ExDataType;
import base_SOM_Objects.som_managers.SOM_MapManager;
import base_SOM_Objects.som_mapnodes.base.SOM_MapNode;
import base_SOM_Objects.som_segments.segments.SOM_MappedSegment;
import strafford_SOM_PKG.straff_SOM_Examples.Straff_SOMMapNode;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

public class Straff_SOM_NonProdJPGCatSegment extends SOM_MappedSegment {
	
	public final Integer nonProdJPGroup;
	
	public Straff_SOM_NonProdJPGCatSegment(SOM_MapManager _mapMgr, Integer _npJpg) {
		super(_mapMgr);
		nonProdJPGroup = _npJpg;
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * determine whether a node belongs in this segment - base it kind of example and whether it has a bmu or not
	 * @param ex the example to check
	 */
	@Override
	public final boolean doesExampleBelongInSeg(SOM_Example ex) {
		//get type of example from ex
		SOM_ExDataType exType = ex.getType();
		switch (exType) {		
			case Training	: 
			case Testing	: 
			case Product	:
			case Validation	: {
				SOM_MapNode bmu = ex.getBmu();
				if(bmu==null) {return false;}			
				return doesMapNodeBelongInSeg(bmu);
			}
			case MapNode	: {		return doesMapNodeBelongInSeg((SOM_MapNode) ex);}
			case MouseOver	: {		return false;}
		}//switch
		return false;
	}

	
	@Override
	public boolean doesMapNodeBelongInSeg(SOM_MapNode ex) {
		Straff_SOMMapNode straffMapNode = ((Straff_SOMMapNode)ex);
				//return map of jpgs to jps to counts present
		TreeMap<Integer, TreeMap<Integer, Float>> npJpGrpMap = straffMapNode.getMappedNonProdJPGroupCounts();
		return (straffMapNode.getNonProdJpGroupSegment(nonProdJPGroup)== null) && npJpGrpMap.keySet().contains(nonProdJPGroup);
	}

	@Override
	protected void setMapNodeSegment(SOM_MapNode mapNodeEx) {	((Straff_SOMMapNode)mapNodeEx).setNonProdJpGroupSeg(nonProdJPGroup, this);	}
	
	/**
	 * return bmu's value for this segment
	 * @param _bmu
	 * @return
	 */
	@Override
	protected Float getBMUSegmentValue(SOM_MapNode _bmu) {	return ((Straff_SOMMapNode)_bmu).getNonProdJpGroupProb(nonProdJPGroup);	}

	/**
	 * build descriptive string for hdr before bmu output
	 * @return
	 */
	@Override
	protected String _buildBMUMembership_CSV_Hdr() {
		String title = ((Straff_SOMMapManager)mapMgr).getNonProdJPGroupSegmentTitleString(nonProdJPGroup);
		String csvHdr = "Non Prod JP Group Probability,BMU Map Loc";
		return title + "\n" + csvHdr;
	}
	
	/**
	 * return bmu's count of examples for this segment
	 * @param _bmu
	 * @return
	 */
	@Override
	protected Float getBMUSegmentCount(SOM_MapNode _bmu) {  return ((Straff_SOMMapNode)_bmu).getMappedNonProdJPGroupCountAtSeg(nonProdJPGroup);}

}
