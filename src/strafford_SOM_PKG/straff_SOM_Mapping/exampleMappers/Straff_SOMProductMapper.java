package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.*;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_UI_Objects.my_procApplet;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

public class Straff_SOMProductMapper extends Straff_SOMExampleMapper {
		//maps of product arrays, with key for each map being either jpg or jp
	private TreeMap<Integer, ArrayList<ProductExample>> productsByJpg, productsByJp;
	//products don't validate
	public Straff_SOMProductMapper(SOMMapManager _mapMgr, String _exName, String _longExampleName) {		
		super(_mapMgr,  _exName, _longExampleName, false);
		productsByJpg = new TreeMap<Integer, ArrayList<ProductExample>>();
		productsByJp = new TreeMap<Integer, ArrayList<ProductExample>>();
	}//ctor

	//specific reset functionality for these type of examples
	@Override
	protected void reset_Priv() {
		productsByJpg.clear();
		productsByJp.clear();
		ProductExample.initAllStaticProdData();
	}//reset_Priv	
	
	///no validation performed for true prospects - all are welcome
	@Override
	protected void validateAndAddExToArray(ArrayList<SOMExample> tmpList, SOMExample ex) {	tmpList.add(ex);}
	@Override
	//add example from map to array without validation
	protected SOMExample[] noValidateBuildExampleArray() {	return (ProductExample[])exampleMap.values().toArray(new ProductExample[0]);};	
	@Override
	protected SOMExample[] castArray(ArrayList<SOMExample> tmpList) {	return (ProductExample[])(tmpList.toArray(new ProductExample[0]));}
	@Override
	//after example array has been built, and specific funcitonality for these types of examples - nothing for products goes here
	protected void buildExampleArrayEnd_Priv(boolean validate) {}
	
	/**
	 * code to execute after examples have had ftrs prepared - this calculates feature vectors
	 */
	@Override
	protected void buildStraffFtrVec_Priv() {
		productsByJpg.clear();
		productsByJp.clear();		
		msgObj.dispMessage("Straff_SOMProductMapper","buildFtrVec_Priv","Jpmon is null : "+ (null==this.jpJpgrpMon),MsgCodes.info5);
		for (SOMExample ex : exampleMap.values()) {		ex.buildFeatureVector();  }
		//once product ftr vecs are built, add products to jp-keyed and jpg-keyed maps
		for (SOMExample ex : exampleMap.values()) {		addProductToJPProductMaps(ex);	}
		
	}//buildFtrVec_Priv
	/**
	 * code to execute after examples have had ftrs calculated - this will calculate std features and any alternate ftr mappings if used
	 */
	@Override
	protected void buildAfterAllFtrVecsBuiltStructs_Priv() {	for (SOMExample ex : exampleMap.values()) {	ex.buildAfterAllFtrVecsBuiltStructs();}}

	
	//add constructed product example to maps holding products keyed by their constituent jps and jpgs
	public void addProductToJPProductMaps(SOMExample exRaw) {
		//add to jp and jpg trees
		ProductExample ex = (ProductExample)exRaw;
		HashSet<Integer> jpgs = new HashSet<Integer>();
		HashSet<Integer> exProdJps = ex.getAllProdJPs();
		//add products to jp-keyed map
		for (Integer jp : exProdJps) {
			ArrayList<ProductExample> exList = productsByJp.get(jp);
			if(exList==null) {exList = new ArrayList<ProductExample>();}
			exList.add(ex);
			productsByJp.put(jp, exList);
			Integer jpg = jpJpgrpMon.getJpgFromJp(jp);
			//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Getting JPG : " + jpg +" for jp : " + jp+".", MsgCodes.warning1);
			jpgs.add( jpg);	//record jp groups this product covers
		}
		//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Size of jpgs : " + jpgs.size() + ".", MsgCodes.warning1);
		for (Integer jpg : jpgs) {
			//msgObj.dispMessage("StraffSOMMapManager","addProductToJPProductMaps","Get JPG : " + jpg +".", MsgCodes.warning1);
			ArrayList<ProductExample> exList = productsByJpg.get(jpg);
			if(exList==null) {exList = new ArrayList<ProductExample>();}
			exList.add(ex);
			productsByJpg.put(jpg, exList);	
		}
	}//addProductToProductMaps	

	@Override
	public void loadAllPreProccedMapData(String subDir) {
		msgObj.dispMessage("Straff_SOMProductMapper","loadAllProductMapData","Loading all product map data", MsgCodes.info5);
		//clear out current product data
		reset();
		//load data creation date time, if exists
		loadDataCreateDateTime(subDir);
		
		String[] loadSrcFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Load(subDir, "productMapSrcData");
		String dataFile =  loadSrcFNamePrefixAra[0]+".csv";
		String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile, "Product Data file loaded", "Product Data File Failed to load");
		//ignore first entry - header
		for (int j=1;j<csvLoadRes.length; ++j) {
			String str = csvLoadRes[j];
			int pos = str.indexOf(',');
			String oid = str.substring(0, pos);
			ProductExample ex = new ProductExample((Straff_SOMMapManager)mapMgr, oid, str);
			exampleMap.put(oid, ex);			
		}
		setAllDataLoaded();
		setAllDataPreProcced();
		msgObj.dispMessage("Straff_SOMProductMapper","loadAllProductMapData","Finished loading and preprocessing all local prospect map data and calculating features.  Number of entries in productMap : " + exampleMap.size(), MsgCodes.info5);
	}//loadAllPreProccedMapData
	
	//save all pre-processed product data
	@Override
	public boolean saveAllPreProccedMapData() {
		if ((null != exampleMap) && (exampleMap.size() > 0)) {
			msgObj.dispMessage("Straff_SOMProductMapper","saveAllPreProccedMapData","Saving all product map data : " + exampleMap.size() + " examples to save.", MsgCodes.info5);
			//save date/time of data creation
			saveDataCreateDateTime();
			
			String[] saveDestFNamePrefixAra = projConfigData.buildPreProccedDataCSVFNames_Save("productMapSrcData");
			ArrayList<String> csvResTmp = new ArrayList<String>();		
			ProductExample ex1 = (ProductExample) exampleMap.get(exampleMap.firstKey());
			String hdrStr = ex1.getRawDescColNamesForCSV();
			csvResTmp.add( hdrStr);	
			for (SOMExample ex : exampleMap.values()) {			
				csvResTmp.add(ex.getPreProcDescrForCSV());
			}
			fileIO.saveStrings(saveDestFNamePrefixAra[0]+".csv", csvResTmp);		
			msgObj.dispMessage("Straff_SOMProductMapper","saveAllPreProccedMapData","Finished saving all product map data", MsgCodes.info5);
			return true;
		} else {msgObj.dispMessage("Straff_SOMProductMapper","saveAllPreProccedMapData","No product example data to save. Aborting", MsgCodes.error2); return false;}
	}//saveAllPreProccedMapData
	
	
	private static int dispProdJPDataFrame = 0, curProdJPIdx = -1, curProdTimer = 0;
	//display the region of the map expected to be impacted by the products serving the passed jp 
	public void drawProductRegion(my_procApplet pa, int prodJpIDX, double maxDist, int distType) {
		pa.pushMatrix();pa.pushStyle();
		ArrayList<ProductExample> prodsToShow = productsByJp.get(jpJpgrpMon.getProdJpByIdx(prodJpIDX));
		if(curProdJPIdx != prodJpIDX) {
			curProdJPIdx = prodJpIDX;
			dispProdJPDataFrame = 0;
			curProdTimer = 0;
		}
		ProductExample ex = prodsToShow.get(dispProdJPDataFrame);
		ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);		
		ex.drawProdMapExtent(pa, distType, prodsToShow.size(), maxDist);
		++curProdTimer;
		if(curProdTimer > 20) {
			curProdTimer = 0;
			dispProdJPDataFrame = (dispProdJPDataFrame + 1) % prodsToShow.size();
		}
		pa.popStyle();pa.popMatrix();	
	}//drawProductRegion
	
	//draw all product nodes with max vals corresponding to current JPIDX
	public void drawProductNodes(my_procApplet pa, int prodJpIDX, boolean showJPorJPG) {
		pa.pushMatrix();pa.pushStyle();
		ArrayList<ProductExample> prodsToShow = (showJPorJPG ? productsByJp.get(jpJpgrpMon.getProdJpByIdx(prodJpIDX)) :  productsByJpg.get(jpJpgrpMon.getProdJpGrpByIdx(prodJpIDX)));
		for(ProductExample ex : prodsToShow) {			ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);		}		
		pa.popStyle();pa.popMatrix();
	}//drawProductNodes	

	private static int dispProdDataFrame = 0, numDispProdDataFrames = 20, framesPerDisp = 0, maxFramesPerDisp = 10;
	//show all products
	public void drawAllProductNodes(my_procApplet pa) {
		pa.pushMatrix();pa.pushStyle();
		if (SOMexampleArray.length-numDispProdDataFrames <=  0 ) {	for(int i=0;i<SOMexampleArray.length;++i){		SOMexampleArray[i].drawMeMap(pa);	}} 
		else {
			for(int i=dispProdDataFrame;i<SOMexampleArray.length-numDispProdDataFrames;i+=numDispProdDataFrames){		SOMexampleArray[i].drawMeMap(pa);	}
			for(int i=(SOMexampleArray.length-numDispProdDataFrames);i<SOMexampleArray.length;++i){		SOMexampleArray[i].drawMeMap(pa);	}				//always draw these (small count < numDispDataFrames
			++framesPerDisp;
			if(framesPerDisp >= maxFramesPerDisp) {
				framesPerDisp = 0;
				dispProdDataFrame = (dispProdDataFrame + 1) % numDispProdDataFrames;
			}
		}
		//for(ProductExample ex : productData) {ex.drawMeLinkedToBMU(pa, 5.0f,ex.OID);}		
		pa.popStyle();pa.popMatrix();
	}//drawProductNodes



}//class Straff_SOMProductMapper
