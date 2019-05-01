package base_SOM_Objects.som_vis;

import java.util.TreeMap;
import java.util.concurrent.Callable;

import base_SOM_Objects.*;
import base_SOM_Objects.som_examples.*;
import base_Utils_Objects.*;
import processing.core.PImage;

//this will build a single image of the map based on ftr data
public class SOMFtrMapVisImgBuilder implements Callable<Boolean>{
	private MessageObject msgObj;
	private int mapX, mapY, xSt, xEnd, ySt, yEnd, imgW;
	//type of features to use to build vis, based on type used to train map (unmodified, stdftrs, normftrs)
	private int ftrType;
	private float mapScaleVal, sclMultXPerPxl, sclMultYPerPxl;
	private TreeMap<Tuple<Integer,Integer>, SOMMapNode> MapNodes;
	private PImage[] mapLocClrImg;
	public SOMFtrMapVisImgBuilder(SOMMapManager _mapMgr, PImage[] _mapLocClrImg, int[] _xVals, int[] _yVals,  float _mapScaleVal) {
		msgObj= _mapMgr.buildMsgObj();
		MapNodes = _mapMgr.getMapNodes();
		ftrType = _mapMgr.getCurrentTrainDataFormat();
		mapLocClrImg = _mapLocClrImg;
		mapX = _mapMgr.getMapNodeCols();
		xSt = _xVals[0];
		xEnd = _xVals[1];
		if(mapLocClrImg.length == 0) {		imgW = 0;} 
		else {								imgW = mapLocClrImg[0].width;}
		mapY = _mapMgr.getMapNodeRows();
		ySt = _yVals[0];
		yEnd = _yVals[1];

		mapScaleVal = _mapScaleVal;
		sclMultXPerPxl = mapScaleVal * _mapMgr.getNodePerPxlCol();
		sclMultYPerPxl = mapScaleVal * _mapMgr.getNodePerPxlRow();
	}//ctor
	
	public float[] getMapNodeLocFromPxlLoc(float mapPxlX, float mapPxlY){	return new float[]{(mapPxlX * sclMultXPerPxl) - .5f, (mapPxlY * sclMultYPerPxl) - .5f};}	
		
	//get treemap of features that interpolates between two maps of features
	private TreeMap<Integer, Float> interpTreeMap(TreeMap<Integer, Float> a, TreeMap<Integer, Float> b, float t, float mult){
		TreeMap<Integer, Float> res = new TreeMap<Integer, Float>();
		float Onemt = 1.0f-t;
		if(mult==1.0) {
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*Onemt) + (bVal*t));			
			}
		} else {//scale by mult - precomputes color values
			float m1t = mult*Onemt, mt = mult*t;
			//first go through all a values
			for(Integer key : a.keySet()) {
				Float aVal = a.get(key), bVal = b.get(key);
				if(bVal == null) {bVal = 0.0f;}
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}
			//next all b values
			for(Integer key : b.keySet()) {
				Float aVal = a.get(key);
				if(aVal == null) {aVal = 0.0f;} else {continue;}		//if aVal is not null then calced already
				Float bVal = b.get(key);
				res.put(key, (aVal*m1t) + (bVal*mt));			
			}			
		}		
		return res;
	}//interpolate between 2 tree maps
	
	//return interpolated feature vector on map at location given by x,y, where x,y is float location of map using mapnodes as integral locations
	private TreeMap<Integer, Float> getInterpFtrs(float x, float y){
		int xInt = (int) Math.floor(x+mapX)%mapX, yInt = (int) Math.floor(y+mapY)%mapY, xIntp1 = (xInt+1)%mapX, yIntp1 = (yInt+1)%mapY;		//assume torroidal map		
		float xInterp = (x+1) %1, yInterp = (y+1) %1;
		//always compare standardized feature data in test/train data to standardized feature data in map
		TreeMap<Integer, Float> LowXLowYFtrs = MapNodes.get(new Tuple<Integer, Integer>(xInt,yInt)).getCurrentFtrMap(ftrType), 
				LowXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xInt,yIntp1)).getCurrentFtrMap(ftrType),
				HiXLowYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yInt)).getCurrentFtrMap(ftrType),  
				HiXHiYFtrs= MapNodes.get(new Tuple<Integer, Integer>(xIntp1,yIntp1)).getCurrentFtrMap(ftrType);
		try{
			TreeMap<Integer, Float> ftrs = interpTreeMap(interpTreeMap(LowXLowYFtrs, LowXHiYFtrs,yInterp,1.0f),interpTreeMap(HiXLowYFtrs, HiXHiYFtrs,yInterp,1.0f),xInterp,255.0f);	
			return ftrs;
		} catch (Exception e){
			msgObj.dispMessage("SOMFtrMapVisImgBuilder","getInterpFtrs","Exception triggered in mySOMMapUIWin::getInterpFtrs : \n"+e.toString() + "\n\tMessage : "+e.getMessage() , MsgCodes.error1);
			return null;
		}		
	}//getInterpFtrs	
	
	private int getDataClrFromFtrVec(TreeMap<Integer, Float> ftrMap, Integer jpIDX) {
		Float ftrVal = ftrMap.get(jpIDX);
		if(ftrVal == null) {return 0;}
		int ftr = Math.round(ftrVal);
		int clrVal = ((ftr & 0xff) << 16) + ((ftr & 0xff) << 8) + (ftr & 0xff);
		return clrVal;
	}//getDataClrFromFtrVec	
	
	@Override
	public Boolean call() throws Exception {
		//build portion of every map in each thread-  this speeds up time consuming interpolation between neighboring nodes for each location
		float[] c;
		for(int y = ySt; y<yEnd; ++y){
			int yCol = y * imgW;
			for(int x = xSt; x < xEnd; ++x){
				int pxlIDX = x+yCol;
				c = getMapNodeLocFromPxlLoc(x, y);
				TreeMap<Integer, Float> ftrs = getInterpFtrs(c[0],c[1]);
				//for (int i=0;i<mapLocClrImg.length;++i) {	mapLocClrImg[i].pixels[x+yCol] = getDataClrFromFtrVec(ftrs, i);}
				//only access map that the interpolated vector has values for
				for (Integer jp : ftrs.keySet()) {mapLocClrImg[jp].pixels[pxlIDX] = getDataClrFromFtrVec(ftrs, jp);}
			}
		}
		return true;
	}	
}//SOMFtrMapVisImgBuilder