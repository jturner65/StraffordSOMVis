package base_SOM_Objects.som_segments.segments;

import java.util.TreeMap;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.*;

/**
 * this class will manage instances of segments built off feature weight - these segments will overlap with one another - there is one segment per ftr idx
 * @author john
 *
 */
public final class SOM_FtrWtSegment extends SOM_MappedSegment {
	public final int ftrIDX;
	public final int ftrCalcType;

	public SOM_FtrWtSegment(SOM_MapManager _mapMgr, int _ftrIDX) {
		super(_mapMgr);
		ftrIDX = _ftrIDX;
		//TODO may wish to allow for different types of ftrs to be used to build segment
		//normalized equalizes all map nodes; stdized equalizes features
		ftrCalcType=SOM_MapManager.useNormedDat;
	}//ctor
	
	/**
	 * determine whether a node belongs in this segment - base it on BMU
	 * @param ex the example to check
	 */
	@Override
	public final boolean doesExampleBelongInSeg(SOM_Example ex) {
		SOM_MapNode mapNode = ex.getBmu();
		if(mapNode == null) {return false;}		//if no bmu then example does not belong in any segment
		return doesMapNodeBelongInSeg(mapNode);		
	}

	/**
	 * determine whether a mapnode belongs in this segment getFtrWtSegment
	 */
	public final boolean doesMapNodeBelongInSeg(SOM_MapNode _bmu) {
		TreeMap<Integer, Float> ftrs = _bmu.getCurrentFtrMap(ftrCalcType);
		Float ftrAtIDX = ftrs.get(ftrIDX);
		return ((_bmu.getFtrWtSegment(ftrIDX)== null) && (ftrAtIDX!=null) && (ftrAtIDX > 0.0f));
	}

	@Override
	protected final void setMapNodeSegment(SOM_MapNode mapNodeEx) {	mapNodeEx.setFtrWtSeg(ftrIDX, this);	}

	/**
	 * return bmu's value for this segment
	 * @param _bmu
	 * @return
	 */
	@Override
	protected final Float getBMUSegmentValue(SOM_MapNode _bmu) {	return _bmu.getCurrentFtrMap(ftrCalcType).get(ftrIDX);}

	/**
	 * build descriptive string for hdr before bmu output
	 * @return
	 */
	@Override
	protected final String _buildBMUMembership_CSV_Hdr() {
		String title = mapMgr.getFtrWtSegmentTitleString(ftrCalcType, ftrIDX);
		String csvHdr = "ftrVal,count,BMU Map Loc";
		return title + "\n" + csvHdr;
	}

	@Override
	protected Float getBMUSegmentCount(SOM_MapNode _bmu) {return 1.0f;}

}//class SOM_FtrWtSegment
