package strafford_SOM_PKG;


import base_UI_Objects.GUI_AppManager;
import base_UI_Objects.my_procApplet;
import base_UI_Objects.windowUI.base.myDispWindow;
import strafford_SOM_PKG.straff_UI.Straff_SOMMapUIWin;
/**
 * Testbed to visually inspect and verify results from Strafford prospect mapping to a SOM
 * 
 * John Turner
 * 
 */
public class Strafford_SOM_Mapper_UI_Main extends GUI_AppManager {
	//project-specific variables
	private String prjNmLong = "Testbed for development of Strafford Prospects SOM", prjNmShrt = "SOM_Strafford";
				
	private final int
		showUIMenu = 0,
		showSOMMapUI = 1;
	public final int numVisFlags = 2;
	
	//idx's in dispWinFrames for each window - 0 is always left side menu window
	private static final int	dispSOMMapIDX = 1;	
																		//set array of vector values (sceneFcsVals) based on application
	//private boolean cyclModCmp;										//comparison every draw of cycleModDraw			
	private final int[] bground = new int[]{244,244,244,255};		//bground color	
	
	/**
	 * Labels for buttons that describe what mouse-over on the SOM displays
	 */
	public static final String[] MseOvrLblsAra = new String[]{"Loc","Dist","Pop","Ftr","JP","JPGrp","None"};
///////////////
//CODE STARTS
///////////////	
	//////////////////////////////////////////////// code
	
	//needs main to run project - do not modify this code in any way
	//appletArgs = array of single string holding  <pkgname>.<classname> of this class
	public static void main(String[] passedArgs) {		
		Strafford_SOM_Mapper_UI_Main me = new Strafford_SOM_Mapper_UI_Main();
		my_procApplet._invokedMain(me, passedArgs);
	}//main	

	@Override
	protected void setRuntimeArgsVals(String[] _passedArgs) {
	}

	/**
	 * whether or not we want to restrict window size on widescreen monitors
	 * 
	 * @return 0 - use monitor size regardless
	 * 			1 - use smaller dim to be determine window 
	 * 			2+ - TBD
	 */
	@Override
	protected int setAppWindowDimRestrictions() {	return 1;}	

	@Override
	protected void setSmoothing() {		pa.setSmoothing(0);		}
	
	
	//instance-specific setup code
	@Override
	protected void setup_Indiv() {setBkgrnd();}	
	
	@Override
	protected void setBkgrnd(){
		((my_procApplet)pa).background(bground[0],bground[1],bground[2],bground[3]);		
	}//setBkgrnd

	/**
	 * determine which main flags to show at upper left of menu 
	 */
	@Override
	protected void initMainFlags_Indiv() {
		setMainFlagToShow_debugMode(false);
		setMainFlagToShow_saveAnim(true); 
		setMainFlagToShow_runSim(false);
		setMainFlagToShow_singleStep(false);
		setMainFlagToShow_showRtSideMenu(true);
	}
	
	@Override
	//build windows here
	protected void initAllDispWindows() {
		showInfo = true;
		//drawnTrajEditWidth = 10;
		//includes 1 for menu window (never < 1) - always have same # of visFlags as myDispWindows
		int numWins = numVisFlags;		
		//titles and descs, need to be set before sidebar menu is defined
		String[] _winTitles = new String[]{"","SOM Map UI"},
				_winDescr = new String[] {"", "Visualize Prospect SOM Node Mapping"};
		initWins(numWins,_winTitles, _winDescr);
		//call for menu window
		buildInitMenuWin(showUIMenu);
		//menu bar init
		int wIdx = dispMenuIDX,fIdx=showUIMenu;
		String[] menuBtnTitles = new String[]{"Raw Data Conversion/Processing","Load Post Proc Data","Console Exec Testing","Load Prebuilt Maps"};

		String[][] menuBtnNames = new String[][] {	//each must have literals for every button defined in side bar menu, or ignored
			{"Load All Raw ---", "---","Recalc Features"},	//row 1
			{"Train Data","True Prspcts", "Prods", "SOM Cfg"},	//row 2
			{"Train->Bld Map","Map All Data", "---", "---"},	//row 3
			{"Map 1","Map 2","Map 3","Map 4"},
			{"Raw","Proced","JpJpg","MapDat","---"}	
		};
		String[] dbgBtnNames = new String[] {"Debug 0","Debug 1","Debug 2","Debug 3","Debug 4"};
		
		dispWinFrames[wIdx] = buildSideBarMenu(wIdx, fIdx, menuBtnTitles, menuBtnNames, dbgBtnNames, false, true);//new Straff_SOMMapUISideBarMenu(this, winTitles[wIdx], fIdx, winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx]);	
		//instanced window dimensions when open and closed - only showing 1 open at a time
		float[] _dimOpen  =  new float[]{menuWidth, 0, pa.getWidth()-menuWidth, pa.getHeight()}, _dimClosed  =  new float[]{menuWidth, 0, hideWinWidth, pa.getHeight()};	
		//(int _winIDX, float[] _dimOpen, float[] _dimClosed, String _ttl, String _desc, 
		setInitDispWinVals(dispSOMMapIDX, _dimOpen, _dimClosed,
				//boolean[] _dispFlags : idxs : 0 : canDrawInWin; 1 : canShow3dbox; 2 : canMoveView; 3 : dispWinIs3d
				//int[] _fill, int[] _strk, int _trajFill, int _trajStrk)
				new boolean[] {false,false,false,false}, new int[]{50,40,20,255},new int[]{255,255,255,255},new int[] {120,120,120,255},new int[]{50,40,20,255}); 		
		wIdx = dispSOMMapIDX; fIdx=showSOMMapUI;
		dispWinFrames[wIdx] = new Straff_SOMMapUIWin(pa, this, winTitles[wIdx], fIdx, winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx]);		
		//specify windows that cannot be shown simultaneously here
		initXORWins(new int[]{showSOMMapUI},new int[]{dispSOMMapIDX});
	}//	initVisOnce_Priv
	
	@Override
	//called from base class, once at start of program after vis init is called - set initial windows to show - always show UI Menu
	protected void initOnce_Indiv(){
		//which objects to initially show
		setVisFlag(showUIMenu, true);					//show input UI menu	
		setVisFlag(showSOMMapUI, true);
	}//	initOnce
	
	@Override
	//called multiple times, whenever re-initing
	protected void initProgram_Indiv(){	}//initProgram	
	@Override
	protected void initVisProg_Indiv() {}		

	@Override
	protected String getPrjNmLong() {return prjNmLong;}
	@Override
	protected String getPrjNmShrt() {return prjNmShrt;}
	
	/**
	 * Individual extending Application Manager post-drawMe functions
	 * @param modAmtMillis
	 * @param is3DDraw
	 */
	@Override
	protected void drawMePost_Indiv(float modAmtMillis, boolean is3DDraw) {}
	
	/**
	 * present an application-specific array of mouse over btn names 
	 * for the selection of the desired mouse over text display - if is length 0 or null, will not be displayed
	 */
	@Override
	public String[] getMouseOverSelBtnLabels() {
		return MseOvrLblsAra;
	}
	
	//////////////////////////////////////////////////////
	/// user interaction
	//////////////////////////////////////////////////////	
	//key is key pressed
	//keycode is actual physical key pressed == key if shift/alt/cntl not pressed.,so shift-1 gives key 33 ('!') but keycode 49 ('1')
	
	/**
	 * handle non-numeric keys being pressed
	 * @param keyVal character of key having been pressed
	 * @param keyCode actual code of key having been pressed
	 */
	@Override
	protected void handleKeyPress(char keyVal, int keyCode) {
		switch (keyVal){
			case ' ' : {toggleSimIsRunning(); break;}							//run sim
			case 'f' : {dispWinFrames[curFocusWin].setInitCamView();break;}					//reset camera
			case 'a' :
			case 'A' : {toggleSaveAnim();break;}						//start/stop saving every frame for making into animation
			case 's' :
			case 'S' : {break;}//{saveSS(prjNmShrt);break;}//save picture of current image			
			default : {	}
		}//switch	
		
		
	}//handleNonNumberKeyPress

	@Override
	//gives multiplier based on whether shift, alt or cntl (or any combo) is pressed
	public double clickValModMult(){return ((altIsPressed() ? .1 : 1.0) * (shiftIsPressed() ? 10.0 : 1.0));}	
	//keys/criteria are present that means UI objects are modified by set values based on clicks (as opposed to dragging for variable values)
	//to facilitate UI interaction non-mouse computers, set these to be single keys
	@Override
	public boolean isClickModUIVal() {
		//TODO change this to manage other key settings for situations where multiple simultaneous key presses are not optimal or conventient
		return altIsPressed() || shiftIsPressed();		
	}
	
	@Override
	//these tie using the UI buttons to modify the window in with using the boolean tags - PITA but currently necessary
	public void handleShowWin(int btn, int val, boolean callFlags){//display specific windows - multi-select/ always on if sel
		if(!callFlags){//called from setflags - only sets button state in UI to avoid infinite loop
			//setMenuBtnState(mySideBarMenu.btnShowWinIdx,btn, val);
		} else {//called from clicking on buttons in UI
		//val is btn state before transition 
		boolean bVal = (val == 1?  false : true);
		switch(btn){
			case 0 : {setVisFlag(showSOMMapUI, bVal);break;}
			}
		}
	}//handleShowWin
	
	@Override
	//get the ui rect values of the "master" ui region (another window) -> this is so ui objects of one window can be made, clicked, and shown displaced from those of the parent windwo
	public float[] getUIRectVals(int idx){
		//this.pr("In getUIRectVals for idx : " + idx);
		switch(idx){
		case dispMenuIDX 		: {return new float[0];}			//idx 0 is parent menu sidebar
		case dispSOMMapIDX 	: {	return dispWinFrames[dispMenuIDX].uiClkCoords;}
		default :  return dispWinFrames[dispMenuIDX].uiClkCoords;
		}
	}	
	
	//////////////////////////////////////////
	/// graphics and base functionality utilities and variables
	//////////////////////////////////////////
	
	/**
	 * return the number of visible window flags for this application
	 * @return
	 */
	@Override
	public int getNumVisFlags() {return numVisFlags;}
	@Override
	//address all flag-setting here, so that if any special cases need to be addressed they can be
	protected void setVisFlag_Indiv(int idx, boolean val ){
		switch (idx){
			case showUIMenu 	    : { dispWinFrames[dispMenuIDX].setFlags(myDispWindow.showIDX,val);    break;}											//whether or not to show the main ui window (sidebar)			
			case showSOMMapUI		: {setWinFlagsXOR(dispSOMMapIDX, val); break;}
			default : {break;}
		}
	}//setFlags  


	/**
	 * any instancing-class-specific colors - colorVal set to be higher than IRenderInterface.gui_OffWhite
	 * @param colorVal
	 * @param alpha
	 * @return
	 */
	@Override
	public int[] getClr_Custom(int colorVal, int alpha) {	return new int[] {255,255,255,alpha};}



}//class SOM_StraffordMain
