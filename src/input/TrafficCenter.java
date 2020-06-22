package input;

import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ExtenderException;
import exceptions.InOutException;
import exceptions.JNIException;
import exceptions.MiscException;
import exceptions.PackingException;
import exceptions.ParserException;
import packing.PackData;
import packing.PackExtender;
import util.BoolParser;
import util.CallPacket;
import util.ForSpec;
import util.ResultPacket;
import util.StringUtil;

/**
 * Controls execution traffic for 'CirclePack'. Intended as a
 * a thread handling essentially all execution calls so GUI can
 * continue operating on its thread. Handles: 
 * 1) immediate GUI, housecleaning, etc. (where efficiency important)
 * 2) Main work, pick off individual commands from strings, and send
 *    them to 'CommandStrParser.jexecute'. 
 *    
 *    Preprocessing includes (can be sensitive to order of processing
 *    and must avoid infinite recursion):
 *    
 *    * resolving 'script' named commands
 *    * finding double quoted strings
 *    * processes "-p?" packing number flags
 *    * processes ":=" variable assignment
 *    * processes "_*" variable substitution
 *    * processes !! repeats, recursive calls
 *    * catches 'CirclePack' "for/FOR" loops
 *    * handles "delay" calls (in appropriate thread)
 *    * manages 'PackExtender' handoffs
 *    
 * 3) "background" thread (or those likely to last long?)
 *    Might want separate progress indicator? stop button?
 * 
 * NOTE: Lots of issues on self/cross-reference, sequence of processing,
 * sequence of execution, result/error reporting, history, etc.
 */
public class TrafficCenter {

	protected String lastCmd;
	protected static final int MAX_DEPTH=5; // max recursion depth

	// Constructor
	public TrafficCenter() {
		lastCmd=new String("");
	}

	/**
	 * Top level for processing strings of commands, executed in
	 * the top level persistent thread of execution processing.
	 *  * Find calls to 'for', 'FOR', '!!', or 'delay'; these 
	 *    remain in this top thread so that effects are seen as
	 *    they happen 
	 *  * Other calls are passed to 'parseCmdSeq' after starting
	 *    another, temporary thread.
	 *  * Sends results[] to 'MsgFrame.processCmdResults'.
	 *    Gather messages and/or errors, update 'history' panel.
	 *  TODO: plan to objectify this with 'ResultPacket's
	 *  
	 * @param cmd String, the command string
	 * @param p PackData
	 * @param remember boolean: if true, keep this as past command 
	 * @param useThread boolean: true, then run in thread and don't wait
	 * @param dep int, recursion depth
	 * @return int: <=0 on error, 1 for going to another thread, 
	 *         n for number of successful commands. 
	 */
	public int parseWrapper(String cmd, PackData p, boolean remember,
			boolean useThread, int dep,MyConsole myc) {
		final String cmdf = new String(cmd.trim());
		final PackData packData = p;
		final boolean mf = remember;
		final int depth = dep;
		final MyConsole mycon=myc;
		if (depth > 0 || cmdf.contains("!!"))
			useThread = false;
		final boolean threadOK = useThread;

		try {
			
			Thread workerThread = new Thread(new Runnable() {
				public void run() {
					if (CPBase.cmdDebug) {
						System.out.println("new 'workerThread' in TrafficCenter");
						System.out.flush();
					}
					CPBase.runSpinner.startstop(true);
//					System.out.println("set owl on");
					try {
						ResultPacket rP=new ResultPacket(packData,cmdf);
						rP.memoryFlag=mf;
						CPBase.trafficCenter.parseCmdSeq(rP, depth, mycon);
						ShellManager.processCmdResults(rP,mycon);
					} catch (Exception ex) {
						CirclePack.cpb.errMsg("'TrafficCenter' work thread error: "
								+ ex.getMessage());
					}
					CPBase.runSpinner.startstop(false);
//					System.out.println("turn owl off");
				}
			});
			workerThread.start();
			
//			System.out.println("cmd="+cmdf+". threadOK is "+threadOK);
			
			// wait for thread to finish before continuing
			if (!threadOK) { 
//				System.out.println("waiting for thread");
				workerThread.join();
//				System.out.println("done with thread");
			}

		} catch (Exception e) {}
		return 1; 
	}

	/**
	 * Parse a sequence of command strings, call for successive execution of
	 * individual commands, reap results, messages, etc. 
	 * 
	 * 'ResultPacket' is provided by the calling routine for carrying
	 * information both ways: command string, command count, error messages,
	 * memoryFlag, packing, etc. 
	 * @param rP ResultPacket
	 * @param depth int; used in recursive calls
	 * @param mcs MyConsole, where to report some results
	 */
	public void parseCmdSeq(ResultPacket rP,int depth,MyConsole mcs) {

		int cmdCount = 0;
		
		// CAUTION: 'packnum' is overall pack number; commands can change 
		//    it globally or can change it temporarily just for itself.
		int packnum = rP.packData.packNum;
		int newpnum = packnum;

		// clear MyConsole displays
		PackControl.consoleCmd.dispConsoleMsg("");
		PackControl.consoleActive.dispConsoleMsg("");
		PackControl.consolePair.dispConsoleMsg("");
		
		StringBuilder cmdbldr=new StringBuilder(rP.origCmdString);
		String[] cmds=null;
		
		// Manage case of double-quoted strings: new as of 6/2020
		if (cmdbldr.indexOf("\"")>=0) { // a double quote occurs
			
			// break into alternating unquoted/quoted segments
			Vector<StringBuilder> sbvec=StringUtil.cmdSplitter(cmdbldr);
			
			if (sbvec==null || sbvec.size()==0) {
				cmds = rP.origCmdString.split(";");
			}
			else {
				cmds=new String[sbvec.size()];
				Iterator<StringBuilder> sls=sbvec.iterator();
				int tick=0;
				while (sls.hasNext()) {
					cmds[tick++]=sls.next().toString();
				}
			}
		}
		else {
			cmds = rP.origCmdString.split(";");
		}

		// cycle through the individual commands.
		for (int j = 0; j < cmds.length; j++) {
			
			newpnum=packnum;
			cmds[j] = cmds[j].trim();
			if (cmds[j].length()==0) // skip if ';;'
				continue;
		
			// catch "for" and "FOR" here (and consolidate remaining cmds[.] to use in loop).
			if (cmds[j].startsWith("for ") || cmds[j].startsWith("FOR ") || cmds[j].startsWith("For ")) {
				StringBuilder wholeStr=new StringBuilder(cmds[j]+";");
				for (int J=j+1;J<cmds.length;J++)
					wholeStr.append(cmds[J]+";");
				// 'for' loops run their own threads
				if (CPBase.cmdDebug) {
					System.out.println("new 'for' loop thread");
					System.out.flush();
				}
				forWrapper(CPBase.pack[packnum].getPackData(),wholeStr.toString(),mcs);
				cmdCount++;
				break; // to break out of 'for' loop on cmd[j]
			} // end of 'for' catch
			
			// catch 'IF THEN (ELSE)' here (and consolidate remaining as part of if-then)
			if (cmds[j].startsWith("IF")) {
				StringBuilder wholeStr=new StringBuilder(cmds[j]+";");
				for (int J=j+1;J<cmds.length;J++)
					wholeStr.append(cmds[J]+";");
				int cmdadd=ifThenWrapper(CPBase.pack[packnum].getPackData(),wholeStr.toString(),mcs,rP);
				if (rP.interrupt) {
					rP.msgs=new String("'IF' failed");
					rP.cmdCount=cmdCount+cmdadd;
					return;
				}
				cmdCount +=cmdadd;
				break;
			} // end of 'if-then' catch
			
			// debugging
			if (CPBase.cmdDebug) {
				System.out.println("cmd: "+cmds[j]);
				System.out.flush();
			}
			
			// user interrupt, ignore rest of commands 
			if(cmds[j].startsWith("break")) {
				rP.msgs=new String("'break' interrupted commands");
				rP.interrupt=true;
				rP.cmdCount=cmdCount;
				return;
			} // end of 'break' catch

			// global change in 'PackData' for active pack
			if (cmds[j].startsWith("act")) {

				// pack number could be from a variable
				String ss = StringUtil.varSub(cmds[j].substring(3).trim());

				newpnum = -1;
				try {
					newpnum = Integer.parseInt(ss);
				} catch (NumberFormatException e) {
					rP.errorMsgs = new String("malformed pack number");
					rP.cmdCount = -cmdCount;
					return;
				}
				if (newpnum < 0 || newpnum >= CPBase.NUM_PACKS) {
					rP.errorMsgs = new String("illegal pack number");
					rP.msgs = Integer.valueOf(-cmdCount).toString();
					return;
				}
				PackControl.switchActivePack(newpnum);
				packnum = newpnum; // change in global pack number
				cmdCount++;
				continue; // continue for loop on cmds[j]
			} // end of 'act' catch

			if (cmds[j].startsWith("delay ")) {
				try {
					// get remaining portion of command
					cmds[j] = cmds[j].substring(6).trim();
					if (cmds[j].length() == 0) {
						rP.errorMsgs = new String("usage: delay {x}");
						rP.msgs = Integer.valueOf(-cmdCount).toString();
						return;
					}
					// could be a variable 
					if (cmds[j].length()>1 && cmds[j].charAt(0)=='_') 
						cmds[j] = StringUtil.varSub(cmds[j]);
					double delay =Double.parseDouble(cmds[j]);
					if (delay > 10)
						delay = 10; // limit 10 seconds

					// here's the actual sleep
					if (delay > 0.0) {
						Thread.sleep((long) (delay * 1000.0));
					}
					cmdCount++;
				} 
				catch (InterruptedException ie) {}
				catch (Exception ex) {
					rP.errorMsgs = new String("usage: delay {x}");
					rP.msgs = Integer.valueOf(-cmdCount).toString();
					return;
				}
				continue; // continue loop on cmds[j]
			} // end of 'delay' catch
			
			// Catch ":=" variable-setting calls: vname:=value
			//   Note: this option must be processed further below,
			//   since here we just change 'cmds[j]' to 'set_variable'
			if (cmds[j].contains(":=")) {
				int k = cmds[j].indexOf(":=");
				if (k < 1) {
					rP.errorMsgs = new String("malformed ':=' attempt");
					rP.msgs = new String("-" + cmdCount);
					return;
				}
				String vname = cmds[j].substring(0, k).trim();
				// vname can't contain a space
				int m = vname.lastIndexOf(" ");
				if (m > 0)
					vname = vname.substring(m + 1);
				
				// common mistake: first char might be '_' (which should be used 
				//     only for referring to existing variable)
				if (vname.charAt(0)=='_') { 
					if (vname.length()==1) {
						rP.errorMsgs = new String("malformed ':=' attempt. Extraneous '_' perhaps?");
						rP.msgs = new String("-" + cmdCount);
						return;
					}
					vname=vname.substring(1); // omit the '_' symbol
				}
				
				k +=2; // move beyond ":="
					
				// rest = string value of this variable, may have variables itself 
				if (k>=cmds[j].length() || cmds[j].substring(k).trim().length()==0)
					continue; 
				String valu = StringUtil.varSub(cmds[j].substring(k).trim());
				if (valu==null || valu.length()==0)
					continue; // skip, continue loop on cmds[j]
				// re-form command, will continue processing 
				cmds[j]=new String("set_variable "+vname+" "+valu);
				// TODO: in processing 'var' command look for '{...}' structure
				//    to set to some value returned by a function.
			} // end of ':=' catch

			// ***** Commands with recursion: '!!', [.]
			// '!!' is call to repeat lastCmd: to avoid infinite recursion, 
			//    '!!' is never included in 'lastCmd'. Also, '!!' 
			//    terminates the rest of the current command sequence, 
			//    except it allows several '!!' commands in immediate 
			//    succession.
			//   'lastCmd' will remain unchanged, so !! can be used again.
			if (cmds[j].charAt(0)=='!' && cmds[j].charAt(1)=='!') {
				String redoCmd = new String(lastCmd);
				ResultPacket rsP=new ResultPacket(CPBase.pack[newpnum].getPackData(),redoCmd);
				parseCmdSeq(rP,0,mcs);
				int repeat_count=rsP.cmdCount;
				lastCmd = new String(redoCmd);
				// if command failed or there's another command not !!
				if (repeat_count < 0
						|| (j == (cmds.length - 1) || !cmds[j + 1]
								.contains("!!"))) {
					rP.cmdCount=-cmdCount;
					return;
				} 
				cmdCount += repeat_count;
				continue;
			} // end of '!!' catch

			// ******** Substitute from script for '[.]' named commands
			if (cmds[j].charAt(0)=='[') {
				if (!CPBase.scriptManager.isScriptLoaded()) {
					String fe = new String(
							"error: '[.]' call fails, no script is loaded");
					rP.errorMsgs=rP.errorMsgs.concat(fe);
					rP.cmdCount=-cmdCount;
					return;
				}
				int k = cmds[j].indexOf(']');
				if (k < 0) {
					rP.errorMsgs=rP.errorMsgs.concat("cmd format error in '[.]': right bracket missing");
					rP.cmdCount=-cmdCount;
					return;
				}
				String brktcmd = null;
				if (k == 1) // empty bracket? do next
					brktcmd = (String) CPBase.scriptManager.getCommandString();
				else {
					if (depth > MAX_DEPTH) {
						String fe = new String(
								"Command parsing error: "
										+ "recursive search depth for command replacement exceeded.");
						PackControl.consoleCmd.dispConsoleMsg(fe);
						rP.errorMsgs=rP.errorMsgs.concat(fe);
						rP.cmdCount=-cmdCount;
						return;
					}
					
					String key=(String) cmds[j].substring(1, k);
					
					// could be a variable 
					if (key.length()>1 && key.charAt(0)=='_') 
						key = StringUtil.varSub(key);
					brktcmd=(String)CPBase.scriptManager.findCmdByName(key,1);
					if (brktcmd==null) { // named command not found
						String fe = new String("Named script command [" + key
								+ "] not found");
						PackControl.consoleCmd.dispConsoleMsg(fe);
						rP.errorMsgs=rP.errorMsgs.concat(fe);
						return;
					}
				}

				// here's recursive call
				ResultPacket uP=new ResultPacket(CPBase.pack[newpnum].getPackData(),brktcmd);
				uP.memoryFlag=rP.memoryFlag;
				parseCmdSeq(uP,depth+1,mcs);
				if (uP.errorMsgs!=null && uP.errorMsgs.trim().length()>0)
					rP.errorMsgs=rP.errorMsgs.concat(";"+uP.errorMsgs);
				if (uP.msgs!=null && uP.msgs.trim().length()>0)
					rP.msgs = rP.msgs.concat(";"+uP.msgs);
				cmdCount += uP.cmdCount; 
				continue; // continue loop on cmds[j]
			} // end of '[.]' catch
			
			// for remaining cases, need to check for and remove "-p" flag

			// First, handle -p flag to temporarily change packing
			if (cmds[j].contains("-p")) {
				StringBuilder sb=new StringBuilder(cmds[j]);
				newpnum=StringUtil.extractPackNum(sb);
				if (newpnum<0)
					newpnum=packnum;
				cmds[j]=sb.toString(); // new command with -p flag removed
			} // end of '-p' catch

			// ********** handoff to 'PackExtender'? (.e.g. '|BQ|')
			if (cmds[j].charAt(0) == '|') {
				String[] cmdcontent = StringUtil.getXtender(cmds[j]);
				PackExtender pXdr = null;
				if (cmdcontent != null
						&& cmdcontent[1].length() > 0
						&& (pXdr = CPBase.pack[newpnum].getPackData()
								.findXbyAbbrev(cmdcontent[0])) != null) {
					int k = cmdcontent[1].indexOf(' ');
					String cmd = null;
					Vector<Vector<String>> flagSegs=null;
					if (k > 0) {
						cmd = cmdcontent[1].substring(0, k).trim();
						flagSegs= StringUtil.flagSeg(cmdcontent[1].substring(k));
					}
					else {
						cmd = new String(cmdcontent[1]);
					}
					int rslt = pXdr.cmdParser(cmd, flagSegs);
					if (rslt <= 0) { // error
						rP.errorMsgs = new String("extender '" + cmdcontent[0]
								+ "' cmd failed");
						rP.cmdCount=-cmdCount;
						return;
					} else 
						cmdCount += 1;
				} else {
					rP.errorMsgs = new String("failed extender attempt");
					rP.cmdCount=-cmdCount;
					return;
				}
			} // end of 'extender' handoff
			

			// ===================================================
			// ===== here's where most commands are handled ======
			// ===================================================

			else {
				
				boolean valueCall=false; // fork to reap command value?

				// ***** Special case: 'get_variable'. user may accidently put '_' in 
				//      front of name, then 'varSub' will screw things up. So
				//      replace an extraneous '_' by a blank (there should be 
				//      at most one '_', and it should be proceeded by a blank)
				if (cmds[j].startsWith("get_var")) {
					int k=cmds[j].indexOf(' '); // first blank
					// If you see '_' after k, it should be replaced by a blank
					if (cmds[j].indexOf('_',k)>0) {
						cmds[j]=new String(cmds[j].substring(0,k)+cmds[j].substring(k).replace(" _"," "));
					}
				}
				
				// *********** catch '{..cmd..}' for value call 
				if (cmds[j].charAt(0) == '{') {
					String bracesstr=StringUtil.getBracesString(cmds[j]);
					if (bracesstr==null || bracesstr.length()==0) {
						rP.errorMsgs = new String("empty braces");
						rP.cmdCount=-cmdCount;
						return;
					}
					valueCall=true;
					cmds[j]=bracesstr;
				}

				StringBuilder sbld = new StringBuilder(cmds[j]);

				// apply varSub to occurrences of '_', but only AFTER the
				//    command itself (many of which have '_').
				int k=sbld.indexOf(" ");
				if (k<=0 || sbld.indexOf("_",k+1)<=0) {
					cmds[j]=sbld.toString(); 
				}
				else 
					cmds[j]=new String(sbld.substring(0,k)+" "+
							StringUtil.varSub(cmds[j].substring(k+1)));
				
				// -------------------------------------------
				// Here's the typical call to execute single commands
				// ------------------------------------------

				int count = 0;
				String errMsg = null;
				try {
					if (!valueCall) // this is the typical situation
						count = CommandStrParser.jexecute(
								CPBase.pack[newpnum].getPackData(), cmds[j]);
					else { // this puts resulting value in 'CPcallPacket'
						CPBase.CPcallPacket=CommandStrParser.valueExecute(CPBase.pack[newpnum].getPackData(), cmds[j]);
						if (CPBase.CPcallPacket==null || CPBase.CPcallPacket.error) {
							rP.errorMsgs = new String("value computation, {..cmd..} failed");
							rP.cmdCount=-cmdCount;
							return;
						}
						count++;
					}
				} catch (ParserException pex) {
					errMsg = new String("ParserException: // in " + cmds[j]	+ " " 
							+ pex.getMessage() + "; ");
				} catch (CombException cex) {
					errMsg = new String("CombException: "
							+ cex.getMessage()); // + " in " + cmds[j] + "; ");
				} catch (MiscException cex) {
					errMsg = new String("MiscException: "
							+ cex.getMessage()); // + " in " + cmds[j] + "; ");
				} catch (PackingException cex) {
					errMsg = new String("PackingException: "
							+ cex.getMessage()); // + " in " + cmds[j] + "; ");
				} catch (DataException cex) {
					errMsg = new String("DataException: "
							+ cex.getMessage()); // + " in " + cmds[j] + "; ");
				} catch (InOutException cex) {
					errMsg = new String("InOutException: "
							+ cex.getMessage()); // + " in " + cmds[j] + "; ");
				} catch (NullPointerException nex) {
					System.out.println(nex.toString());
					nex.printStackTrace(System.out);
					errMsg = new String("NullPointerException: "
							+ nex.getMessage()); // + " in " + cmds[j] + "; ");
//				} catch (IOException iox) {
//					errMsg = new String("IOException: " + iox.getMessage()
//							+ " in " + cmds[j] + "; ");
				} catch (ExtenderException iox) {
					errMsg = new String(iox.getMessage());
				} catch (JNIException jni) {
					errMsg = new String("JNIException: "+jni.getMessage());
				} catch (Exception ex) {
					errMsg = new String("Exception: " // in " + cmds[j] + ": "
							+ ex.getMessage() + "; ");
				}
				if (errMsg != null || count == 0) {
					cmdCount += count;
					if (errMsg != null) {
						if (rP.errorMsgs!=null)
							rP.errorMsgs=rP.errorMsgs.concat(errMsg);
						else rP.errorMsgs=new String(errMsg);
					}
					rP.cmdCount=-cmdCount;
					return;
				}
				cmdCount++;
			} // end of catch-all 'else'
		} // end of 'for' loop through cmds[j].

		if (rP.memoryFlag)
			lastCmd = new String(rP.origCmdString);
		rP.cmdCount=cmdCount;
		return;
	}
		
	/**
	 * Not yet sure when this will be used; in general, value returns are stored
	 * in the static 'CPBase.CallPacket', but there may be a way to get and
	 * use them directly.
	 * @param rP
	 * @param depth
	 * @param mcs
	 * @return CallPacket
	 */
	public CallPacket parseValueCall(ResultPacket rP,int depth,MyConsole mcs) {
		CallPacket rtnCp=null;
		
		// TODO: have to do preprocessing as in 'parseCmdSeq' to catch, -p flag, etc.
		
		return rtnCp;
	}
	
	/**
	 * Wrapper to handle "if-then-else" commands. The functionality is basic,
	 * not sophisticated. The form is: IF <condition> THEN <cmds> ELSE <cmds>
	 * 
	 * The <condition> is set by the string between IF and THEN and should
	 * contain no ';'. It must evaluate to true/false, possibly with reference to the 
	 * active pack and static variables and commands that return
	 * some value, e.g. a boolean or a double.
	 * 
	 * THEN is followed by a named command [.]. ELSE is optional and 
	 * also followed by a named command; default is to issue a 'break'. 
	 * 
	 * These structures can be nested only to the extent that the 
	 * named commands might themselves contain IF-THEN-ELSE.
	 *
	 *@param p PackData
	 *@param wholeStr String
	 *@param myc MyConsole
	 *@param rP ResultPacket, only to transmit 'interrupt', if appropriate
	 */
	public static int ifThenWrapper(PackData p,String wholeStr,MyConsole myc,ResultPacket rP) {
		int ifk=wholeStr.indexOf("IF");
		int thenk=wholeStr.indexOf("THEN");
		int elsek=wholeStr.indexOf("ELSE");
		
		// Is this an 'IF-THEN' statement
		if (ifk!=0 || thenk<0) 
			return -1;
		
		// get the condition string
		String condition=wholeStr.substring(ifk+2, thenk).trim();
		if (condition.contains(";"))
			return -1;
		
		// get then and else commands
		String thenStr=null;
		String elseStr=null;
		if (elsek<0) {
			thenStr=wholeStr.substring(thenk+4).trim();
			elseStr="break";
		}
		else {
			thenStr=wholeStr.substring(thenk+4,elsek).trim();
			elseStr=wholeStr.substring(elsek+4).trim();
		}
		
		Boolean outcome=BoolParser.trueFalse(p,condition);
		if (outcome==null) {
			throw new ParserException("if-then condition failed to evaluate correctly");
		}
		
		// carry out the 'then' or 'else' commands
		ResultPacket innerRP=null;
		if (outcome) 
			innerRP=new ResultPacket(p,thenStr); 
		else 
			innerRP=new ResultPacket(p,elseStr); 

		
		CPBase.trafficCenter.parseCmdSeq(innerRP,0,myc);
		// pass along interrupt signal (e.g. from 'break')
		if (innerRP.interrupt) {
			rP.interrupt=true;
		}
		rP.cmdCount +=innerRP.cmdCount;
		return innerRP.cmdCount;
	}
	
	/**
	 * Wrapper to handle "for" calls. Parse/execute strings of ';' separated
	 * commands with for-loop structure. Sends commands for processing,
	 * consolidates messages and/or errors, updates 'history' panel, counts
	 * iterations. This runs its own execution thread, so user can see output as
	 * it occurs; whether it blocks until finished depends on call from
	 * 'parseWrapper'. 
	 * 
	 * For-loop usage: if delta>0, increment 'start' while <= 'end'. if delta<0,
	 * decrement 'start' while >= 'end'. delta=0 is error.
	 * @param p, 'PackData'
	 * @param wholeStr, full string, starting with "for" or "FOR"
	 * @return 1, since we're firing off new thread
	 */
	public static int forWrapper(PackData p,String wholeStr,MyConsole myc) {
		double delay=0.0;
		
		// build up the calling string
		StringBuilder forString = new StringBuilder(wholeStr.substring(0,3)+" ");
		StringBuffer restbuf = new StringBuffer(wholeStr.substring(3).trim());
		// initial section should be loop parameters
		int k = StringUtil.getForString(restbuf);
		if (k <= 0) {
			throw new ParserException("malformed 'for' string specs");
		}
		String specStr = restbuf.substring(0, k);
		// build the 'for' specification info
		ForSpec forSpec = StringUtil.getForSpec(specStr);
		if (forSpec == null) {
			throw new ParserException("malformed 'for' string specs");
		}
		forSpec.setItNum();
		// cautionary check to avoid unplanned huge loops
		if (forString.charAt(0)=='f' && forSpec.itNum > 10) {
			throw new ParserException(
					"'for' loop with > 10 repeats requires 'FOR' form");
		}
		// for history
		forString.append(" "+specStr+" ");
		
		restbuf.delete(0,k);
		String cmdTail=restbuf.toString().trim();

		if (cmdTail.startsWith("-d")) {
			cmdTail = cmdTail.substring(2).trim();
			if (cmdTail.length() == 0
					|| (k = cmdTail.indexOf(' ')) < 0) {
				throw new ParserException("-d flag: no {x} given");
			}
			try {
				delay = Double.parseDouble(cmdTail.substring(0, k));
			} catch (Exception ex) {
				throw new ParserException("-d flag: failed to get {x}");
			}
			if (delay < 0.0) {
				throw new ParserException("-d flag: {x} negative");
			}

			// save this -d specification for history
			forString.append(" -d " + cmdTail.substring(0, k) + " ");
			// but remove from 'tailCmds'
			cmdTail = cmdTail.substring(k).trim();
		}

		final PackData fp		   = p;
//		final String forStr        = forString.toString();
		final ForSpec finalForSpec = forSpec;
		final String  fcmd		   = cmdTail;
		final double fdelay	       = delay;
		final MyConsole mycon=myc;
		
		try {
			Thread forThread = new Thread(new Runnable() {
				public void run() {
					CPBase.runSpinner.startstop(true);
					String cmd = fcmd;
					PackData p = fp;

					int accumCount = 0;
					String varName = null;
					if (finalForSpec.varName != null)
						varName = finalForSpec.varName.trim();
					double varVal = finalForSpec.start;
					double varDelta = finalForSpec.delta;
					double start = finalForSpec.start;
					double end = finalForSpec.end;
					double delta = finalForSpec.delta;
					
					double delay = fdelay;

					ResultPacket rP=null;
					boolean named = !(varName == null || varName.length() == 0);
					if (Math.abs(delta) < CPBase.GENERIC_TOLER) {
						throw new ParserException("'for' increment too small");
					}
					if (delta < 0) { // flip increment orientation
						delta *= -1.0;
						start *= -1.0;
						end *= -1.0;
					}

					while (start <= end) {
						
						// update loop variable if named
						if (named) { 
							String newVal = Double.valueOf(varVal).toString();
							Vector<String> itm=new Vector<String>(1);
							itm.add(newVal);
							Vector<Vector<String>> flsg=new Vector<Vector<String>>(1);
							flsg.add(itm);
							CPBase.varControl.putVariable(p,varName,flsg);
						}
						
						try {

							// here's the actual next execution pass
							rP=new ResultPacket(p,cmd);
							CPBase.trafficCenter.parseCmdSeq(rP,0,mycon);
							// check if iteration got interrupt signal (e.g. from 'break')
							if (rP.interrupt) {
								start=end; 
							}
							accumCount += rP.cmdCount;
						} catch (Exception ex) {
							CPBase.runSpinner.startstop(false);
							throw new ParserException(
									"'for' count at exception: " + accumCount);
						}

						if (delay > 0.0) {
							try {
								Thread.sleep((long) (delay * 1000.0));
							} catch (InterruptedException ie) {
							}
						}

						start += delta; // iteration variables
						varVal += varDelta; // increment real variables
						accumCount++;
					} // end of big while

					// store results in messages and history; info is from
					// last execution, but count is accumulated
					try {
						rP.cmdCount=accumCount;
						rP.memoryFlag=true;
						ShellManager.processCmdResults(rP,mycon);
					} catch (Exception ex) {
						CPBase.runSpinner.startstop(false);
						throw new ParserException(
								"problem processing results: " + ex.getMessage());
					}
					CPBase.runSpinner.startstop(false);
				}
			});
			forThread.start();

			System.gc(); // suggest garbage collection
			forThread.join();

		} catch (Exception ex) {
		}

		return 1;
	}	
	
	/**
	 * Command that applies to the top level GUI -- as from menus and 
	 * buttons. This run's in 'CirclePack's main thread, not execution
	 * thread (though same 'cmd' might well be processed in other
	 * threads)
	 * TODO: for efficiency, try to move some of these to direct action.
	 * 
	 * @param p, 'PackData'
	 * @param cmd, String (single command)
	 * @return normally <= 0 on error
	 */
	public static int cmdGUI(PackData p,String cmd) {
		try {
			return CommandStrParser.jexecute(p,cmd);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("jexecute error; "+ex.getMessage());
		}
		return 0;
	}
	
	/**
	 * Command that applies to the top level GUI -- as from menus and 
	 * buttons. This run's in 'CirclePack's main thread, not the 
	 * execution thread, and applies to the 'active' packing.
	 * TODO: for efficiency, try to move some of these to direct action.
	 * action.
	 * @param cmd, single command
	 * @return 
	 */
	public static int cmdGUI(String cmd) {
		try {
			return CommandStrParser.jexecute(cmd);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("jexecute problem: "+ex.getMessage());
		}
		return 0;
	}
	
	
}
