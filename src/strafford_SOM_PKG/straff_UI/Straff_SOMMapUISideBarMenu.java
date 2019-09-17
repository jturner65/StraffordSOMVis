package strafford_SOM_PKG.straff_UI;

import base_UI_Objects.my_procApplet;
import base_UI_Objects.windowUI.BaseBarMenu;

/**
 * class to manage buttons used by sidebar window - overrides base setup, allows for custom config
 * @author john
 *
 */
public class Straff_SOMMapUISideBarMenu extends BaseBarMenu {

	public Straff_SOMMapUISideBarMenu(my_procApplet _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed, String _winTxt) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt);
	}

	@Override
	protected void initSideBarMenuBtns_Priv() {
		/**
		 * set row names for each row of ui action buttons getMouseOverSelBtnNames()
		 * @param _funcRowNames array of names for each row of functional buttons 
		 * @param _numBtnsPerFuncRow array of # of buttons per row of functional buttons
		 * @param _numDbgBtns # of debug buttons
		 * @param _inclWinNames include the names of all the instanced windows
		 * @param _inclMseOvValues include a row for possible mouse over values
		 */
		
		setBtnData(new String[]{"Raw Data Conversion/Processing","Load Post Proc Data","Console Exec Testing","Load Prebuilt Maps"}, new int[] {3,4,4,4}, 5, false, true);
		

	}


}//mySideBarMenu