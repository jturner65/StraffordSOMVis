package strafford_SOM_PKG.straff_UI;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeMap;

import base_Render_Interface.IRenderInterface;
import base_Math_Objects.vectorObjs.doubles.myPoint;
import base_Math_Objects.vectorObjs.doubles.myVector;
import base_SOM_Objects.som_managers.SOM_MapManager;
import base_SOM_Objects.som_ui.win_disp_ui.SOM_MapUIWin;
import base_SOM_Objects.som_ui.win_disp_ui.SOM_MseOvrDispTypeVals;
import base_UI_Objects.GUI_AppManager;
import base_UI_Objects.windowUI.uiObjs.base.GUIObj_Type;
import base_Utils_Objects.io.messaging.MsgCodes;
import base_UI_Objects.windowUI.uiObjs.GUIObj_List;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

//window that accepts trajectory editing
public class Straff_SOMMapUIWin extends SOM_MapUIWin {
	
	/**
	 * idxs of boolean values/flags - instance-specific
	 */
	public static final int 
		custExCalcedIDX					= numSOMBasePrivFlags + 0,			//whether customer prospect examples have been loaded and ftrs have been calculated or not  StraffWeightCalc.bndAra_ProdJPsIDX StraffWeightCalc.bndAra_AllJPsIDX
		tpExCalcedIDX					= numSOMBasePrivFlags + 1,			//whether true propsect examples have been loaded and ftrs have been calculated or not
		trainExCalcedIDX				= numSOMBasePrivFlags + 2,			//whether training data examples have been loaded and ftrs have been calculated or not - these are per-order training examples
		
		mapDrawPrdctFtrBMUsIDX 			= numSOMBasePrivFlags + 3,			//draw product bmu as calculated by ftrs
		mapDrawCurProdFtrBMUZoneIDX	 	= numSOMBasePrivFlags + 4,			//show currently selected prod jps' products and influence zones, as calculated by ftrs
		//non-product segments
		mapDrawNonProdJPSegIDX			= numSOMBasePrivFlags + 5,			//draw segment defined by the non-product jps present in the training examples mapped to SOM 
		mapDrawNonProdJPGroupSegIDX		= numSOMBasePrivFlags + 6,			//draw segment defined by the non-product jp groups present in the training examples mapped to SOM 
		
		//display/interaction
		mapDrawTruePspctIDX				= numSOMBasePrivFlags + 7,			//draw true prospect examples on map		
		mapDrawCustAnalysisVisIDX		= numSOMBasePrivFlags + 8,			//whether or not to draw feature calc analysis graphs for customer examples
		mapDrawTPAnalysisVisIDX			= numSOMBasePrivFlags + 9,			//whether or not to draw feature calc analysis graphs for true prospect examples
		mapDrawTrainDataAnalysisVisIDX	= numSOMBasePrivFlags + 10,			//whether or not to draw feature calc analysis graphs for training data examples (this will be relevant when training data is individual per-order-derived
						
		mapDrawCalcFtrOrAllVisIDX		= numSOMBasePrivFlags + 11,			//whether to draw calc obj for ftr-related jps, or all jps present		
		
		showSelJPIDX					= numSOMBasePrivFlags + 12, 			//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
		//train/test data managemen
		procTruProspectsIDX				= numSOMBasePrivFlags + 13,			//this will process true prospects, and load them if they haven't been loaded
		saveBMUMapsForTruPrspctsIDX		= numSOMBasePrivFlags + 14;			//this will save all the product data for the currently selected prod JP
	/**
	 * need to specify how many private state flags are in use
	 */
	private final int _numPrivFlags = numSOMBasePrivFlags + 15;			//size to set up priv flags array
	
	//	//GUI Objects	
	public final static int //offset from end of base class SOM UI objs
		uiRawDataSourceIDX 			= numSOMBaseGUIObjs + 0,			//source of raw data to be preprocced and used to train the map
		uiProdJPToDispIDX			= numSOMBaseGUIObjs + 1,			//choose current product/zone to show
		uiProdZoneDistThreshIDX		= numSOMBaseGUIObjs + 2,			//max distance from a product that a map node should be considered to be covered by that product
		uiAllJpSeenToDispIDX		= numSOMBaseGUIObjs + 3;			//choose jp to show based on all jps seen
	
	//public final int numGUIObjs = numSOMBaseGUIObjs + 4;
	
	
	//raw data source : 0 == csv, 1 == sql
	private int rawDataSource;
		
	/////////
	//custom debug/function ui button names -empty will do nothing
	public String[][] menuBtnNames = new String[][] {	//each must have literals for every button defined in side bar menu, or ignored
		{"Load All Raw ---", "---","Recalc Features"},	//row 1
		{"Train Data","True Prspcts", "Prods", "SOM Cfg"},	//row 2
		{"Train->Bld Map","Map All Data", "---", "---"},	//row 3
		{"Map 1","Map 2","Map 3","Map 4"},
		{"Raw","Proced","JpJpg","MapDat","---"}	
	};
	//private final String[] dfltPreBltMapNames = {"Map 1","Map 2","Map 3","Map 4"};
	//used to switch button name for 1st button to reflect whether performing csv-based load of raw data or sql query
	private String[] menuLdRawFuncBtnNames = new String[] {"CSV", "SQL"};
	
	public Straff_SOMMapUIWin(IRenderInterface _p, GUI_AppManager _AppMgr, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt) {
		super(_p,_AppMgr, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt);
		super.initThisWin(false);
	}//ctor
	
	/**
	 * Instancing class-specific (application driven) UI buttons to display are built 
	 * in this function.  Add an entry to tmpBtnNamesArray for each button, in the order 
	 * they are to be displayed
	 * @param tmpBtnNamesArray array list of Object arrays, where in each object array : 
	 * 			the first element is the true string label, 
	 * 			the 2nd elem is false string array, and 
	 * 			the 3rd element is integer flag idx 
	 * @return total number of privBtnFlags in instancing class (including those not displayed)
	 */
	@Override
	protected final int initAllSOMPrivBtns_Indiv(ArrayList<Object[]> tmpBtnNamesArray) {
		tmpBtnNamesArray.add(new Object[] {"Hide Non-Product Job Practices","Show Non-Product Job Practices", mapDrawNonProdJPSegIDX});          
		tmpBtnNamesArray.add(new Object[] {"Hide Non-Product Job Practice Groups", "Show Non-Product Job Practice Groups", mapDrawNonProdJPGroupSegIDX});			
		tmpBtnNamesArray.add(new Object[] {"Hide Products (ftr BMUs)","Show Products (ftr BMUs)", mapDrawPrdctFtrBMUsIDX});          
		tmpBtnNamesArray.add(new Object[] {"Hide Cur Prod Zone (by ftrs)", "Show Cur Prod Zone (by ftrs)", mapDrawCurProdFtrBMUZoneIDX});	
		tmpBtnNamesArray.add(new Object[] {"Show Calc Plot on Ftr JPs", "Show Calc Plot on All JPs", mapDrawCalcFtrOrAllVisIDX});     
		tmpBtnNamesArray.add(new Object[] {"Hide Training Data Calc Plot", "Show Training Data Calc Plot", mapDrawTrainDataAnalysisVisIDX});
		tmpBtnNamesArray.add(new Object[] {"Hide Cust Prspct Calc Plot", "Show Cust Prspct Calc Plot", mapDrawCustAnalysisVisIDX});     
		tmpBtnNamesArray.add(new Object[] {"Hide True Prspct Calc Plot", "Show True Prspct Calc Plot", mapDrawTPAnalysisVisIDX});       
		tmpBtnNamesArray.add(new Object[] {"Hide True Prospects on Map", "Show True Prospects on Map", mapDrawTruePspctIDX});           
		tmpBtnNamesArray.add(new Object[] {"Mapping True Prospect BMUs", "Map True Prospect BMUs", procTruProspectsIDX});           
		tmpBtnNamesArray.add(new Object[] {"Building/Saving Tru Prspct BMUs for loaded Map","Build/Save Tru Prspct BMUs for loaded Map", saveBMUMapsForTruPrspctsIDX}); 
		return 	this._numPrivFlags;

	}//initAllSOMPrivBtns_Indiv
	/**
	 * Instance class determines the true and false labels the class-category locking should use
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of whether 
	 * category should be locked to allow selection through within-category classes
	 */
	@Override
	protected String[] getClassCatLockBtnTFLabels() {return new String[] {"JPGroup Changes with JP","Lock JPGroup; restrict JPs to JPG"};}

	/**
	 * Instance class determines the true and false labels the class buttons use - if empty then no classes used
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of class-based segment
	 */
	@Override
	protected final String[] getClassBtnTFLabels() {	return new String[] {"Hide Order-Based JP Segments  ","Show Order-Based JP Segments  "};}
	/**
	 * Instance class determines the true and false labels the category buttons use - if empty then no categories used
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control display of category-based segment
	 */
	@Override
	protected final String[] getCategoryBtnTFLabels() {	return new String[] {"Hide Order-Based JPGroup Segments", "Show Order-Based JPGroup Segments"};}	
	
	/**
	 * This will return instance class-based true and false labels for save segment data.  if empty then no segment saving possible
	 * @return array holding true(idx0) and false(idx1) labels for buttons to control saving of segment data
	 */
	@Override
	protected final String[] getSegmentSaveBtnTFLabels() {return new String[] {"Saving Class, Category and Feature weight segment BMUs", "Save Class, Category and Feature weight segment BMUs" };}

	@Override
	protected void initMeIndiv() {
		//based on width of map
		((Straff_SOMMapManager) mapMgr).setAnalysisHt((mapMgr.getMapHeight()*.45f));
		//for single jp detail display
		((Straff_SOMMapManager) mapMgr).setAnalysisPerJPWidth((mapMgr.getMapWidth()*.1f));
		//default to having calc objects display analysis on ftrs 
		privFlags.setFlag(mapDrawCalcFtrOrAllVisIDX, true);
		// capable of using right side menu
		dispFlags.setDrawRtSideMenu(true);
		//dataFrmtToUseToTrain = (int)(this.guiObjs[uiTrainDataFrmtIDX].getVal()); 
		((Straff_SOMMapManager) mapMgr).setProdZoneDistThresh(this.guiObjs[uiProdZoneDistThreshIDX].getVal());
		rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());

		//moved from mapMgr ctor, to remove dependence on papplet in that object
		mapMgr.initMapAras(1, 1);
	}//initMeIndiv()
		
	@Override
	//SOM_mapDims is built by base class initMe
	protected SOM_MapManager buildMapMgr() {
		//SOM_mapDims : start x and y and dimensions of full map visualization as function of visible window size;
		//including strings for default directories specific to current project setup and Strafford
		TreeMap<String, Object> _argsMap = new TreeMap<String,Object>();
		//provide default values used by program
		_argsMap.put("configDir", "StraffordProject" + File.separator+"config" + File.separator);
		_argsMap.put("dataDir", "StraffordProject" + File.separator);
		_argsMap.put("logLevel",0);//0 is console alone,1 is log file alone, 2 is both
		
		return new Straff_SOMMapManager(_argsMap);
	}
	
	@Override
	protected void setVisScreenDimsPriv_Indiv() {
		//now build calc analysis offset struct
		((Straff_SOMMapManager) mapMgr).setCalcAnalysisLocs();
		((Straff_SOMMapManager) mapMgr).setAnalysisAllJPBarWidth(curVisScrDims[0]);		
	}
	
//	//per jp bar width ~= total width / # of jps
//	protected void setAnalysisDimWidth() {
//		((Straff_SOMMapManager) mapMgr).setAnalysisAllJPBarWidth(curVisScrDims[0]);	
//	}
	

	@Override
	protected void setPrivFlagsIndiv(int idx, boolean val) {
		switch (idx) {//special actions for each flag
			case mapDrawTruePspctIDX	: {//draw true prospect examples
				break;}		
			case mapDrawCalcFtrOrAllVisIDX : {
				((Straff_SOMMapManager) mapMgr).setCurCalcAnalysisJPTypeIDX((val ? Straff_WeightCalc.bndAra_ProdJPsIDX : Straff_WeightCalc.bndAra_AllJPsIDX));		
				((Straff_SOMMapManager) mapMgr).setAnalysisAllJPBarWidth(curVisScrDims[0]);	
				break;}			
			
			case mapDrawNonProdJPSegIDX			: {//draw segments defined by non-product jps owned by training examples that mapped to each bmu
				break;}		
			case mapDrawNonProdJPGroupSegIDX	: {//draw segments defined by non-product jp groups owned by training examples that mapped to each bmu
				break;}		
			
			case mapDrawCustAnalysisVisIDX	: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					privFlags.setFlag(mapDrawTPAnalysisVisIDX, false);
					privFlags.setFlag(mapDrawTrainDataAnalysisVisIDX, false);					
					((Straff_SOMMapManager) mapMgr).setCurCalcAnalysisSrcDataTypeIDX(Straff_WeightCalc.custCalcObjIDX);
					((Straff_SOMMapManager) mapMgr).setAnalysisAllJPBarWidth(curVisScrDims[0]);	
				} else {
					
				}
				break;}
			case mapDrawTPAnalysisVisIDX	: {//whether or not to draw feature calc analysis graphs  
				if (val) {//if setting to true then aggregate data
					privFlags.setFlag(mapDrawCustAnalysisVisIDX, false);
					privFlags.setFlag(mapDrawTrainDataAnalysisVisIDX, false);					
					((Straff_SOMMapManager) mapMgr).setCurCalcAnalysisSrcDataTypeIDX(Straff_WeightCalc.tpCalcObjIDX);
					((Straff_SOMMapManager) mapMgr).setAnalysisAllJPBarWidth(curVisScrDims[0]);	
				} else {
					
				}
				break;}
			
			case mapDrawTrainDataAnalysisVisIDX : {
				if (val) {//if setting to true then aggregate data
					privFlags.setFlag(mapDrawCustAnalysisVisIDX, false);
					privFlags.setFlag(mapDrawTPAnalysisVisIDX, false);					
					((Straff_SOMMapManager) mapMgr).setCurCalcAnalysisSrcDataTypeIDX(Straff_WeightCalc.trainCalcObjIDX);
					((Straff_SOMMapManager) mapMgr).setAnalysisAllJPBarWidth(curVisScrDims[0]);	
				}else {
					
				}
				break;}
			case showSelJPIDX		 : {//if showSelRegionIDX == true, then this will show either a selected jp or jpgroup
				break;}
			case procTruProspectsIDX : {
//				if(val) {
//					((StraffSOMMapManager) mapMgr).buildAndSaveTrueProspectReport();	
//					addPrivBtnToClear(procTruProspectsIDX);					
//				}			
				break;}		//put true prospects on the SOM
			case saveBMUMapsForTruPrspctsIDX : {
				if(val) {
					((Straff_SOMMapManager) mapMgr).saveBMUMapsForTruPrspcts();		
					addPrivBtnToClear(saveBMUMapsForTruPrspctsIDX);			
				}				
				break;}		//save all product to prospect mappings given currently selected product jp and dist thresh
			
			case custExCalcedIDX 	: {		break;}			
			case tpExCalcedIDX  	: {		break;}		
			case trainExCalcedIDX 	: {		break;}
		}		
	}//setPrivFlagsIndiv
	/**
	 * Instance-specific code for managing locking of category segment selection to enable cycling through class within category
	 * TODO
	 * @param val whether the lock button is being turned on or off
	 */
	@Override
	protected void setPrivFlags_LockCatForClassSegs(boolean val) {
		if(val) {
			
		} else {
			
		}		
	}//setPrivFlags_LockCatForClassSegs
	
	/**
	 * Instancing class-specific (application driven) UI objects to be shown in left side bar menu 
	 * for this window.  This is the first child class function called by initThisWin
	 * @param tmpUIObjArray : map of object data, keyed by UI object idx, with array values being :                    
	 *           the first element double array of min/max/mod values                                                   
	 *           the 2nd element is starting value                                                                      
	 *           the 3rd elem is label for object                                                                       
	 *           the 4th element is object type (GUIObj_Type enum)
	 *           the 5th element is boolean array of : (unspecified values default to false)
	 *           	{value is sent to owning window, 
	 *           	value is sent on any modifications (while being modified, not just on release), 
	 *           	changes to value must be explicitly sent to consumer (are not automatically sent)}    
	 * @param tmpListObjVals : map of list object possible selection values
	 */
	@Override
	protected final void setupGUIObjsAras_Indiv(TreeMap<Integer,Object[]> tmpUIObjArray, TreeMap<Integer, String[]> tmpListObjVals) {	
		//per object entry : object array of {min,max,mod},stVal,lbl,bool ara
		tmpListObjVals.put(uiRawDataSourceIDX,new String[] {"Prebuilt CSV Files","Data Tables Via SQL"});
		tmpListObjVals.put(uiProdJPToDispIDX, new String[] {"Unknown"}); 
		tmpListObjVals.put(uiAllJpSeenToDispIDX, new String[] {"Unknown"});
		
		tmpUIObjArray.put(uiRawDataSourceIDX,new Object[] {new double[]{0.0, tmpListObjVals.get(uiRawDataSourceIDX).length-1, 1}, 0.0, "Raw Data Source", GUIObj_Type.ListVal, new boolean[]{true}});		//uiRawDataSourceIDX
		tmpUIObjArray.put(uiProdJPToDispIDX,new Object[] {new double[]{0.0, 260, 1.0}, 0.0, "Product JP to Show", GUIObj_Type.ListVal, new boolean[]{true}});			//uiProdJPToDispIDX	
		tmpUIObjArray.put(uiProdZoneDistThreshIDX,new Object[] {new double[]{0.0, 5, .01}, 0.99, "Prod Max Sq Dist", GUIObj_Type.FloatVal, new boolean[]{true}});		//uiProdZoneDistThreshIDX	
		tmpUIObjArray.put(uiAllJpSeenToDispIDX,new Object[] {new double[]{0.0, 260, 1.0}, 0.0, "All JP to Show (Calc Analysis)", GUIObj_Type.ListVal, new boolean[]{true}});			//uiAllJpSeenToDispIDX	

	}//setupGUIObjsArasIndiv
	
	/**
	 * instancing class description for category display UI object - if null or length==0 then not shown/used
	 */
	@Override
	protected String getCategoryUIObjLabel() {		return "JPGrp Segments To Show";}

	/**
	 * instancing class description for class display UI object - if null or length==0 then not shown/used
	 */
	@Override
	protected String getClassUIObjLabel() {			return "JP Segments To Show";}
	
	/**
	 * pass the list of values for the list boxes that use training jp group and jp, in idx order
	 * (ftr idx box, jp/class select and jpgroup/category select
	 * @param ftrVals
	 * @param classVals
	 * @param categoryVals
	 * @param prodVals
	 */
	public void setUI_JPFtrListVals(String[] ftrVals, String[] classVals, String[] categoryVals, String[] prodVals) {
		//lists of display strings for 
		setUI_FeatureListVals(ftrVals);
		setUI_ClassListVals(classVals);
		setUI_CategoryListVals(categoryVals);
		//set product list values
		((GUIObj_List) guiObjs[uiProdJPToDispIDX]).setListVals(prodVals);
		//in super class
		setClass_UIObj(false);
	}//setUI_JPListMaxVals
	
	
	/**
	 * pass the list of values for all jp group and jp list boxes, in idx order
	 * @param jpGrpVals
	 * @param jpVals
	 */
	public void setUI_JPAllSeenListVals(String[] jpGrpVals, String[] jpVals) {
		//refresh max size of guiobj - heavy handed, these values won't change often, and this is called -every draw frame-.
		//guiObjs[uiAllJpSeenToDispIDX].setNewMax(jpLen-1);
		((GUIObj_List) guiObjs[uiAllJpSeenToDispIDX]).setListVals(jpVals);
		//guiObjs[uiAllJpgSeenToDispIDX].setNewMax(jpGrpLen-1);	
	}//setUI_JPListMaxVals
	

	@Override
	protected final int getCategoryFromClass(int _curCatIDX, int _classIDX) { return ((Straff_SOMMapManager) mapMgr).getUI_JPGrpIdxFromFtrJPIdx(_classIDX, _curCatIDX);}
	//((Straff_SOMMapManager) mapMgr).getUI_JPGrpIdxFromFtrJPIdx(curMapImgIDX, _clsIDX);

	@Override
	protected final int getClassFromCategory(int _catIDX, int _curClassIDX) { return ((Straff_SOMMapManager) mapMgr).getUI_FirstJPIdxFromFtrJPGIdx(_catIDX, _curClassIDX);}

	/**
	 * return class label from index - will be instance specific
	 * @param _idx idx from class list box to get class label (used as key in map holding class data in map manager)
	 * @return
	 */
	@Override
	protected final int getClassLabelFromIDX(int _idx) {	return ((Straff_SOMMapManager) mapMgr).getFtrJpByIdx(_idx);	}	
	
	/**
	 * return category label from index - will be instance specific
	 * @param _idx idx from category list box to get category label (used as key in map holding category data in map manager)
	 * @return
	 */
	@Override
	protected final int getCategoryLabelFromIDX(int _idx) {	return ((Straff_SOMMapManager) mapMgr).getFtrJpGroupByIdx(_idx);}
	
//	@Override 
//	//handle instance-specific UI components
//	protected void setUIWinValsIndiv(int UIidx) {
//		switch(UIidx){
//			case uiRawDataSourceIDX  : {//source of raw data
//				rawDataSource = (int)(this.guiObjs[uiRawDataSourceIDX].getVal());
//				//change button display
//				setCustMenuBtnNames();
//				msgObj.dispMessage(className,"setUIWinVals","uiRawDataSourceIDX : rawDataSource set to : " + rawDataSource, MsgCodes.info1);
//				break;}					
//			case uiProdJPToDispIDX : {//product to display, for product influence zones
//				((Straff_SOMMapManager) mapMgr).setCurProdToShowIDX((int)guiObjs[uiProdJPToDispIDX].getVal());				
//				break;}
//			case uiProdZoneDistThreshIDX : {//max distance for a node to be considered a part of a product's "region" of influence		
//				((Straff_SOMMapManager) mapMgr).setProdZoneDistThresh(this.guiObjs[uiProdZoneDistThreshIDX].getVal());			
//				break;}
//	
//			case uiAllJpSeenToDispIDX		: {
//				((Straff_SOMMapManager) mapMgr).setCurAllJPToShowIDX((int)guiObjs[uiAllJpSeenToDispIDX].getVal());
//				break;}	
//		}		
//	}//setUIWinValsIndiv	
	@Override
	protected boolean setUI_IntValsCustom_Indiv(int UIidx, int ival, int oldVal) {
		switch(UIidx){
			case uiRawDataSourceIDX  : {//source of raw data
				rawDataSource = ival;
				//change button display
				setCustMenuBtnLabels();
				msgObj.dispMessage(className,"setUIWinVals","uiRawDataSourceIDX : rawDataSource set to : " + rawDataSource, MsgCodes.info1);
				return true;}					
			case uiProdJPToDispIDX : {//product to display, for product influence zones
				((Straff_SOMMapManager) mapMgr).setCurProdToShowIDX(ival);				
				return true;}
			case uiAllJpSeenToDispIDX		: {
				((Straff_SOMMapManager) mapMgr).setCurAllJPToShowIDX(ival);
				return true;}	
		}		
		return false;
	}

	@Override
	protected boolean setUI_FloatValsCustom_Indiv(int UIidx, float val, float oldVal) {
			switch(UIidx){
			case uiProdZoneDistThreshIDX : {//max distance for a node to be considered a part of a product's "region" of influence		
				((Straff_SOMMapManager) mapMgr).setProdZoneDistThresh(val);			
				break;}
			}
		return false;
	}	
		
	//modify menu buttons to display whether using CSV or SQL to access raw data
	@Override
	protected void setCustMenuBtnLabels() {
		String rplStr = menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length)], baseStr;
		for(int i=0;i<menuBtnNames[0].length-2;++i) {
			baseStr = (String) menuBtnNames[0][i].subSequence(0, menuBtnNames[0][i].length()-3);
			menuBtnNames[0][i] = baseStr + rplStr;
		}
		//menuBtnNames[mySideBarMenu.btnAuxFunc1Idx][loadRawBtnIDX]=menuLdRawFuncBtnNames[(rawDataSource % menuLdRawFuncBtnNames.length) ];
		AppMgr.setAllMenuBtnNames(menuBtnNames);	
	}//setCustMenuBtnNames
	
	@Override
	public void initDrwnTrajIndiv(){}
	@Override
	public void drawCustMenuObjs(){}
	@Override
	protected boolean simMe(float modAmtSec) {return false;}
	//set camera to custom location - only used if dispFlag set
	@Override
	protected void setCameraIndiv(float[] camVals){}
	@Override
	protected void stopMe() {}		
	@Override
	protected void drawOnScreenStuffPriv(float modAmtMillis) {}
	
	@Override
	//set flags that should be set on each frame - these are set at beginning of frame draw
	protected void drawSetDispFlags() {
		privFlags.setFlag(custExCalcedIDX, ((Straff_SOMMapManager) mapMgr).isFtrCalcDone(Straff_WeightCalc.custCalcObjIDX));
		privFlags.setFlag(tpExCalcedIDX, ((Straff_SOMMapManager) mapMgr).isFtrCalcDone(Straff_WeightCalc.tpCalcObjIDX));	
		privFlags.setFlag(trainExCalcedIDX,((Straff_SOMMapManager) mapMgr).isFtrCalcDone(Straff_WeightCalc.trainCalcObjIDX));	
		//checking flag to execute if true
		if(privFlags.getFlag(procTruProspectsIDX)){	((Straff_SOMMapManager) mapMgr).buildAndSaveTrueProspectReport();privFlags.setFlag(procTruProspectsIDX,false);	}			
	}
	
	@Override
	protected void drawMapIndiv() {		
		if (privFlags.getFlag(mapDrawCustAnalysisVisIDX)){	((Straff_SOMMapManager) mapMgr)._drawAnalysis(pa,custExCalcedIDX, mapDrawCustAnalysisVisIDX);	} 
		else if (privFlags.getFlag(mapDrawTPAnalysisVisIDX)){((Straff_SOMMapManager) mapMgr)._drawAnalysis(pa,tpExCalcedIDX, mapDrawTPAnalysisVisIDX);}
		else if (privFlags.getFlag(mapDrawTrainDataAnalysisVisIDX)) {((Straff_SOMMapManager) mapMgr)._drawAnalysis(pa,trainExCalcedIDX, mapDrawTrainDataAnalysisVisIDX);}
	}	
		
	/**
	 * type is row of buttons (1st idx in curCustBtn array) 2nd idx is btn
	 * @param funcRow idx for button row
	 * @param btn idx for button within row (column)
	 * @param label label for this button (for display purposes)
	 */
	@Override
	protected final void launchMenuBtnHndlr(int funcRow, int btn, String label){
		//int btn = curCustBtn[curCustBtnType];
		switch(funcRow) {
		case 0 : {//row 1 of menu side bar buttons
			msgObj.dispMessage(className,"launchMenuBtnHndlr","Click Functions 1 in "+name+" : btn : " + btn, MsgCodes.info4);
			switch(btn){
				case 0 : {	
					//load all data from raw local csvs or sql from db
					((Straff_SOMMapManager) mapMgr).loadAndPreProcAllRawData((rawDataSource==0));//, privFlags.getFlag(useOnlyEvntsToTrainIDX));
					resetButtonState();
					break;}
				case 1 : {	
					resetButtonState();
					break;}
				case 2 : {	
					//recalculate currently loaded/processed data.  will reload/rebuild calc object from file, so if data is changed in format file, this will handle that.
					((Straff_SOMMapManager) mapMgr).reCalcCurrFtrs();
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage(className,"launchMenuBtnHndlr","Unknown Functions 1 btn : "+btn, MsgCodes.warning2);
					break;}
			}	
			break;}//row 1 of menu side bar buttons 
	
		case 1 : {//row 2 of menu side bar buttons
			msgObj.dispMessage(className,"launchMenuBtnHndlr","Click Functions 2 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			//		{"Train Data","True Prspcts", "Prods", "SOM Cfg", "Func 14"},	//row 2

			switch(btn){
				case 0 : {	
					mapMgr.loadPreprocAndBuildTestTrainPartitions(getTrainTestDatPartition(), true);
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).loadAllTrueProspectData();
					resetButtonState();
					break;}
				case 2 : {	
					//this will load all true prospects from preprocessed prospect files.
					resetButtonState();
					break;}
				case 3 : {//load all training data, default map config, and build map
					mapMgr.loadSOMConfig();//pass fraction of data to use for training
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage(className,"launchMenuBtnHndlr","Unknown Functions 2 btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}	
			}
			break;}//row 2 of menu side bar buttons
		case 2 : {//row 3 of menu side bar buttons
			msgObj.dispMessage(className,"launchMenuBtnHndlr","Click Functions 3 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			switch(btn){
				case 0 : {	
					mapMgr.loadTrainDataMapConfigAndBuildMap(false);
					resetButtonState();
					break;}
				case 1 : {	
					((Straff_SOMMapManager) mapMgr).loadAllDataAndBuildMappings();
					resetButtonState();
					break;}
				case 2 : {	
					resetButtonState();
					break;}
				case 3 : {
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage(className,"launchMenuBtnHndlr","Unknown Functions 3 btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}	
			}
			break;}//row 3 of menu side bar buttons
		case 3 : {//row 3 of menu side bar buttons
			msgObj.dispMessage(className,"launchMenuBtnHndlr","Click Functions 3 in "+name+" : btn : " + btn, MsgCodes.info4);//{"Ld&Bld SOM Data", "Load SOM Config", "Ld & Make Map", "Ld Prebuilt Map"},	//row 2
			switch(btn){
				case 0 :
				case 1 : 
				case 2 : 
				case 3 : {//load all training data, default map config, and build map
					int curPreBuiltMapIDX = btn;
					mapMgr.setCurPreBuiltMapIDX(curPreBuiltMapIDX);
					uiUpdateData.setIntValue(uiMapPreBuiltDirIDX, (int) this.guiObjs[uiMapPreBuiltDirIDX].setVal(curPreBuiltMapIDX));
					mapMgr.loadPretrainedExistingMap(btn, true);//runs in thread, button state reset there
					resetButtonState();
					break;}
				default : {
					msgObj.dispMessage(className,"launchMenuBtnHndlr","Unknown Functions 3 btn : "+btn, MsgCodes.warning2);
					resetButtonState();
					break;}	
			}
			break;}//row 3 of menu side bar buttons
		default : {
			msgObj.dispWarningMessage(className,"launchMenuBtnHndlr","Clicked Unknown Btn row : " + funcRow +" | Btn : " + btn);
			break;
		}
		}		
	}//launchMenuBtnHndlr
	
	/**
	 * build SOM_MseOvrDispTypeVals value based on which button was chosen
	 */
	@Override
	protected SOM_MseOvrDispTypeVals handleSideMenuMseOvrDisp_MapBtnToType(int btn, boolean val) {
		//{"Loc","Dist","Pop","Ftr","JP","JPGrp","None"};
		switch(btn){
			case 0 : { return val ? SOM_MseOvrDispTypeVals.mseOvrMapNodeLocIDX : SOM_MseOvrDispTypeVals.mseOvrNoneIDX;} 	//"loc"
			case 1 : { return val ? SOM_MseOvrDispTypeVals.mseOvrUMatDistIDX : SOM_MseOvrDispTypeVals.mseOvrNoneIDX;} 	//u mat dist
			case 2 : { return val ? SOM_MseOvrDispTypeVals.mseOvrMapNodePopIDX : SOM_MseOvrDispTypeVals.mseOvrNoneIDX;}  //pop
			case 3 : { return val ? SOM_MseOvrDispTypeVals.mseOvrFtrIDX : SOM_MseOvrDispTypeVals.mseOvrNoneIDX;}         //ftr
			case 4 : { return val ? SOM_MseOvrDispTypeVals.mseOvrClassIDX : SOM_MseOvrDispTypeVals.mseOvrNoneIDX;}       //jp
			case 5 : { return val ? SOM_MseOvrDispTypeVals.mseOvrCatIDX : SOM_MseOvrDispTypeVals.mseOvrNoneIDX;}         //jpgroup
			case 6 : { return SOM_MseOvrDispTypeVals.mseOvrNoneIDX;}        //none
			default : { return SOM_MseOvrDispTypeVals.mseOvrOtherIDX;}      //other/custom
		}
	}

	@Override
	protected final void handleSideMenuDebugSelEnable(int btn) {
		//{"All->Bld Map","All Dat To Map", "Func 22", "Func 23", "Prblt Map"},	//row 3
		switch(btn){
			case 0 : {	
				((Straff_SOMMapManager) mapMgr).dbgShowAllRawData();
				resetButtonState();
				break;}
			case 1 : {	
				((Straff_SOMMapManager) mapMgr).dbgShowUniqueJPsSeen();
				((Straff_SOMMapManager) mapMgr).dbgShowCalcEqs();
				resetButtonState();
				break;}
			case 2 : {	
				((Straff_SOMMapManager) mapMgr).dbgShowJpJpgrpData();
				resetButtonState();
				break;}
			case 3 : {//show current mapdat status
				((Straff_SOMMapManager) mapMgr).dbgShowSOM_MapDat();
				resetButtonState();
				break;}
			case 4 : {						
				resetButtonState();
				break;}
			default : {
				msgObj.dispMessage(className,"handleSideMenuDebugSelEnable","Unknown Debug btn : "+btn, MsgCodes.warning2);
				resetButtonState();
				break;}
		}				
	}
	@Override
	protected final void handleSideMenuDebugSelDisable(int btn) {
		switch (btn) {
		case 0: {
			break;
		}
		case 1: {
			break;
		}
		case 2: {
			break;
		}
		case 3: {
			break;
		}
		case 4: {
			break;
		}
		default: {
			msgObj.dispMessage(className, "handleSideMenuDebugSelDisable", "Unknown Debug btn : " + btn,MsgCodes.warning2);
			resetButtonState();
			break;
		}
		}
	}

	
	//handle mouseover 
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){
		boolean res = false;
		if(privFlags.getFlag(mapDataLoadedIDX)){ res = checkMouseOvr(mouseX, mouseY);	}
		return res;
	}	
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {
		boolean mod = false;			
		if(privFlags.getFlag(mapDataLoadedIDX)){ mod = this.checkMouseClick(mouseX, mouseY, mseClckInWorld, mseBtn);}
//		if(mod) {return mod;}
//		else {return checkUIButtons(mouseX, mouseY);}
		return mod;
	}
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY,int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {
		boolean mod = false;	
		if(privFlags.getFlag(mapDataLoadedIDX)){ mod = this.checkMouseDragMove(mouseX, mouseY, pmouseX, pmouseY, mouseClickIn3D, mseDragInWorld, mseBtn);}				
		return mod;
	}
	@Override
	protected void hndlMouseRelIndiv() {
		if(privFlags.getFlag(mapDataLoadedIDX)){ this.checkMouseRelease();}		
	}	

	@Override
	public String toString(){
		String res = super.toString();
		return res;
	}

	@Override
	protected void setInitValsForPrivFlags_Indiv() {
		// TODO Auto-generated method stub
		
	}


}//Straff_SOMMapUIWin
