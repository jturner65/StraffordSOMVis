package strafford_SOM_PKG;


import base_UI_Objects.*;
import base_UI_Objects.windowUI.myDispWindow;
import processing.core.*;
/**
 * Testbed to visually inspect and verify results from Strafford prospect mapping to a SOM
 * 
 * John Turner
 * 
 */
public class Strafford_SOM_Mapper_UI_Main extends my_procApplet {
	//project-specific variables
	private String prjNmLong = "Testbed for development of Strafford Prospects SOM", prjNmShrt = "SOM_Strafford";
				
	private int[] visFlags;
	private final int
		showUIMenu = 0,
		showSOMMapUI = 1;
	public final int numVisFlags = 2;
	
	//idx's in dispWinFrames for each window - 0 is always left side menu window
	private static final int	dispSOMMapIDX = 1;	
																		//set array of vector values (sceneFcsVals) based on application
	//private boolean cyclModCmp;										//comparison every draw of cycleModDraw			
	private final int[] bground = new int[]{244,244,244,255};		//bground color	

///////////////
//CODE STARTS
///////////////	
	//////////////////////////////////////////////// code
	
	//needs main to run project - do not modify this code in any way
	public static void main(String[] passedArgs) {		
		String[] appletArgs = new String[] { "strafford_SOM_PKG.Strafford_SOM_Mapper_UI_Main" };
	    if (passedArgs != null) {PApplet.main(PApplet.concat(appletArgs, passedArgs)); } else {PApplet.main(appletArgs);		    }
	}//main	
	
	public void settings(){	size((int)(displayWidth*.95f), (int)(displayHeight*.92f),P3D);	noSmooth();}	
	//instance-specific setup code
	protected void setup_indiv() {setBkgrnd();}	
	@Override
	public void setBkgrnd(){background(bground[0],bground[1],bground[2],bground[3]);}//setBkgrnd	
	/**
	 * determine which main flags to show at upper left of menu 
	 */
	@Override
	protected void initMainFlags_Priv() {
		setMainFlagToShow_debugMode(false);
		setMainFlagToShow_saveAnim(true); 
		setMainFlagToShow_runSim(false);
		setMainFlagToShow_singleStep(false);
		setMainFlagToShow_showRtSideMenu(true);
	}
	
	@Override
	//build windows here
	protected void initVisOnce_Priv() {
		showInfo = true;
		drawnTrajEditWidth = 10;
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
		dispWinFrames[wIdx] = new mySideBarMenu(this, winTitles[wIdx], fIdx, winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx],dispWinFlags[wIdx][dispCanDrawInWinIDX]);	
		//instanced window dimensions when open and closed - only showing 1 open at a time
		float[] _dimOpen  =  new float[]{menuWidth, 0, width-menuWidth, height}, _dimClosed  =  new float[]{menuWidth, 0, hideWinWidth, height};	
		//(int _winIDX, float[] _dimOpen, float[] _dimClosed, String _ttl, String _desc, 
		setInitDispWinVals(dispSOMMapIDX, _dimOpen, _dimClosed,
				//boolean[] _dispFlags : idxs : 0 : canDrawInWin; 1 : canShow3dbox; 2 : canMoveView; 3 : dispWinIs3d
				//int[] _fill, int[] _strk, int _trajFill, int _trajStrk)
				new boolean[] {false,false,false,false}, new int[]{50,40,20,255},new int[]{255,255,255,255},new int[] {120,120,120,255},new int[]{50,40,20,255}); 		
		wIdx = dispSOMMapIDX; fIdx=showSOMMapUI;
		dispWinFrames[wIdx] = new Straff_SOMMapUIWin(this, winTitles[wIdx], fIdx, winFillClrs[wIdx], winStrkClrs[wIdx], winRectDimOpen[wIdx], winRectDimClose[wIdx], winDescr[wIdx],dispWinFlags[wIdx][dispCanDrawInWinIDX]);		
		//specify windows that cannot be shown simultaneously here
		initXORWins(new int[]{showSOMMapUI},new int[]{dispSOMMapIDX});
	}//	initVisOnce_Priv
	
	@Override
	//called from base class, once at start of program after vis init is called - set initial windows to show - always show UI Menu
	protected void initOnce_Priv(){
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
	//main draw loop
	public void draw(){	
		//private draw routine
		_drawPriv();
		surface.setTitle(prjNmLong + " : " + (int)(frameRate) + " fps|cyc curFocusWin : " + curFocusWin);
	}//draw	
	
	//handle pressing keys 0-9
	//keyVal is actual value of key (screen character as int)
	//keyPressed is actual key pressed (shift-1 gives keyVal 33 ('!') but keyPressed 49 ('1')) 
	//need to subtract 48 from keyVal or keyPressed to get actual number
	protected void handleNumberKeyPress(int keyVal, int keyPressed) {
		//use key if want character 
		if (key == '0') {
		} 
		
	}//handleNumberKeyPress
	
	//////////////////////////////////////////////////////
	/// user interaction
	//////////////////////////////////////////////////////	
	//key is key pressed
	//keycode is actual physical key pressed == key if shift/alt/cntl not pressed.,so shift-1 gives key 33 ('!') but keycode 49 ('1')
	public void keyPressed(){
		if(key==CODED) {
			if(!shiftIsPressed()){setShiftPressed(keyCode  == 16);} //16 == KeyEvent.VK_SHIFT
			if(!cntlIsPressed()){setCntlPressed(keyCode  == 17);}//17 == KeyEvent.VK_CONTROL			
			if(!altIsPressed()){setAltPressed(keyCode  == 18);}//18 == KeyEvent.VK_ALT
		} else {	
			//handle pressing keys 0-9 (with or without shift,alt, cntl)
			if ((keyCode>=48) && (keyCode <=57)) { handleNumberKeyPress(((int)key),keyCode);}
			else {					//handle all other (non-numeric) keys
				switch (key){
					case ' ' : {toggleSimIsRunning(); break;}							//run sim
					case 'f' : {dispWinFrames[curFocusWin].setInitCamView();break;}					//reset camera
					case 'a' :
					case 'A' : {toggleSaveAnim();break;}						//start/stop saving every frame for making into animation
					case 's' :
					case 'S' : {save(getScreenShotSaveName(prjNmShrt));break;}//save picture of current image			
					default : {	}
				}//switch	
			}
		}
	}//keyPressed()

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
	@Override
		//init boolean state machine flags for program
	public void initVisFlags(){
		visFlags = new int[1 + numVisFlags/32];for(int i =0; i<numVisFlags;++i){forceVisFlag(i,false);}	
		((mySideBarMenu)dispWinFrames[dispMenuIDX]).initPFlagColors();			//init sidebar window flags
	}		
	@Override
	//address all flag-setting here, so that if any special cases need to be addressed they can be
	public void setVisFlag(int idx, boolean val ){
		int flIDX = idx/32, mask = 1<<(idx%32);
		visFlags[flIDX] = (val ?  visFlags[flIDX] | mask : visFlags[flIDX] & ~mask);
		switch (idx){
			case showUIMenu 	    : { dispWinFrames[dispMenuIDX].setFlags(myDispWindow.showIDX,val);    break;}											//whether or not to show the main ui window (sidebar)			
			case showSOMMapUI		: {setWinFlagsXOR(dispSOMMapIDX, val); break;}
			default : {break;}
		}
	}//setFlags  
	@Override
	//get vis flag
	public boolean getVisFlag(int idx){int bitLoc = 1<<(idx%32);return (visFlags[idx/32] & bitLoc) == bitLoc;}	
	@Override
	public void forceVisFlag(int idx, boolean val) {
		int flIDX = idx/32, mask = 1<<(idx%32);
		visFlags[flIDX] = (val ?  visFlags[flIDX] | mask : visFlags[flIDX] & ~mask);
		//doesn't perform any other ops - to prevent 
	}
	

}//class SOM_StraffordMain
