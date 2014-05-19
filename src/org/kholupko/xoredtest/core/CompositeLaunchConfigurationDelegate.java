package org.kholupko.xoredtest.core;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.osgi.util.NLS;


public class CompositeLaunchConfigurationDelegate extends LaunchConfigurationDelegate {

	public static final String COMPOSITE_LAUNCH_CONFIGURATION_ID = "org.kholupko.xoredtest.core.CompositeLaunchConfigurationType";
	
	public static final String PLUGIN_ID = "org.kholupko.xoredtest.core";
	
	public static final String ATTR_COMPOSITE_LAUNCHES_LIST = DebugPlugin.getUniqueIdentifier() + ".compositeLaunchConfigsMementoModeList";
	
	public static final String MEMENTO_MODE_ITEM_DELIMETER = "|";

	public static final int ERR_STATUS_CODE = 500;

	
	
	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		
		//To save consoles for each launch (prevents its removal after termination)
		final IPreferenceStore prefStore = DebugUIPlugin.getDefault().getPreferenceStore();
		boolean dstore = prefStore.getBoolean(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES);
		prefStore.setValue(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES,	false);
		
		
		List<String> launchConfigsMementoModeList = configuration.getAttribute(ATTR_COMPOSITE_LAUNCHES_LIST, (List)null);
		
		
		// theoretically is not possible
		if(launchConfigsMementoModeList == null || launchConfigsMementoModeList.isEmpty())
			return;
				
		if (monitor == null)
			monitor = new NullProgressMonitor();	

		try {

			monitor.beginTask(NLS.bind("{0}...", new String[]{configuration.getName()}), launchConfigsMementoModeList.size()); 
			
			if (monitor.isCanceled())
				return;								
			
			Boolean doTerminate = false;
			
			for (String mementoModeListItem : launchConfigsMementoModeList) {

				ILaunchConfiguration subLaunchConfiguration = null;
				ILaunch subLaunch = null;
				
				try {
					String[] mementoModeArray = mementoModeListItem.split("\\" + MEMENTO_MODE_ITEM_DELIMETER);

					String subLaunchConfigMemento = mementoModeArray[0];
					String subLaunchConfigMode = mementoModeArray[1];
					
					subLaunchConfiguration = DebugPlugin.getDefault().getLaunchManager().getLaunchConfiguration(subLaunchConfigMemento);
															
					monitor.subTask(NLS.bind("{0}...", new String[]{subLaunchConfiguration.getName()}));
					
					//decides whether to build configuration projects basing upon common preferences
					subLaunch = DebugUIPlugin.buildAndLaunch(subLaunchConfiguration, subLaunchConfigMode, new SubProgressMonitor(monitor, 1));
				}
				catch(CoreException ce){
					DebugPlugin.log(ce);
										
					String message = "Error while launching ";
					if(subLaunchConfiguration != null)
						message += "configuration " + subLaunchConfiguration.getName() + ".";
					else
						message += "one of configurations.";
					
					IStatus status = new Status(IStatus.WARNING, PLUGIN_ID, ERR_STATUS_CODE, message, ce);
					IStatusHandler handler = DebugPlugin.getDefault().getStatusHandler(status);
					if(handler != null){
						doTerminate = (Boolean) handler.handleStatus(status, null);
						if(doTerminate)
							break;
					}
					
				}
				
				// keeps composite launch registered in launchManager, allows to stay in debug view and terminate all sublaunches at once
				if(subLaunch != null){
					IProcess[] processes = subLaunch.getProcesses();
					for(IProcess process : processes)
						launch.addProcess(process);
				}
				
				if (monitor.isCanceled())
					return;

				monitor.worked(1);
			}
			
			if(doTerminate)
				launch.terminate();
			
		}
		//pass all unexpected exceptions further
		finally{
			prefStore.setValue(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES, dstore);
			
			monitor.done();
		}

	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#buildForLaunch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor)
			throws CoreException {
		// Each child launch configuration should decide to build its projects individually
		return false;
	}	

}
