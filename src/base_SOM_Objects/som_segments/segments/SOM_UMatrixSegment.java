package base_SOM_Objects.som_segments.segments;



import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.*;

/**
 * This class will manage an instance of a segment mapped based on UMatrix distance
 * @author john
 */
public class SOM_UMatrixSegment extends SOMMapSegment {
	//threshold of u-dist for nodes to be considered similar. (0..1.0) (not including bounds) and be included in segment
	public float thresh;

	public SOM_UMatrixSegment(SOMMapManager _mapMgr, float _thresh) {	
		super(_mapMgr);
		thresh = _thresh;
	}
	/**
	 * determine whether a node belongs in this segment - base it on BMU
	 * @param ex the example to check
	 */
	@Override
	public boolean doesExampleBelongInSeg(SOMExample ex) {
		SOMMapNode mapNode = ex.getBmu();
		if(mapNode == null) {return false;}		//if no bmu then example does not belong in any segment
		return doesMapNodeBelongInSeg(mapNode);		
	}

	/**
	 * determine whether a mapnode belongs in this segment - only 1 umatrix segment per map node
	 */
	public boolean doesMapNodeBelongInSeg(SOMMapNode ex) {	return ((ex.getUMatrixSegment() == null) && (thresh >= ex.getUMatDist()));}

	/**
	 * Set the passed map node to have this segment as its segment
	 * @param ex map node to set this as a segment
	 */
	@Override
	protected void setMapNodeSegment(SOMMapNode mapNodeEx) {	mapNodeEx.setUMatrixSeg(this);	}

	

}//SOM_UMatrixSegment
