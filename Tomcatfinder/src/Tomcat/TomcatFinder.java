package Tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.net.URL;
import java.net.URLConnection;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

public class TomcatFinder {

	private static int findTomcatPort(Integer port) {
		if (isPortInUse(port)) {
			if (isTomcatRunning(port)) {
				System.out.println("Found running Apache Tomcat instance on Port: " + port);
				return port;
			}

		} else {
			System.out.println("No ports found.");
		}
		return -1;
	}

	private static boolean isPortInUse(int port) {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			return false;
		} catch (IOException e) {
			return true;
		}
	}

	private static boolean isTomcatRunning(int port) {
		return isPortInUse(port);
	}

	private static void killProcess(int pid) {
		try {
			Runtime.getRuntime().exec("kill " + pid);
			System.out.println("Process with PID " + pid + " killed successfully.");
		} catch (IOException e) {
			System.err.println("Error occurred while trying to kill process with PID " + pid);
			e.printStackTrace();
		}
	}

	private static String findXmlTomcatDirectories() {
		try {
			String powerShellCmd = "powershell.exe -ExecutionPolicy Bypass -Command " + "\"$serverXmlPaths = @(); "
					+ "$drives = Get-WmiObject Win32_LogicalDisk | Where-Object { $_.DriveType -eq 3 } | ForEach-Object { $_.DeviceID }; "
					+ "foreach ($drive in $drives) { "
					+ "   $tomcatDirs = Get-ChildItem -Path \\\"$drive\\\\*\\\" -Filter \\\"apache-tomcat-*\\\" -Directory -ErrorAction SilentlyContinue; "
					+ "   if ($tomcatDirs) { " + "       foreach ($tomcatDir in $tomcatDirs) { "
					+ "           $serverXmlPath = Get-ChildItem -Path $tomcatDir.FullName -Filter \\\"server.xml\\\" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName; "
					+ "           if ($serverXmlPath) { " + "               $serverXmlPaths += $serverXmlPath; "
					+ "           } " + "       } " + "   } " + "} " + "$serverXmlPaths\"";

			ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", powerShellCmd);
			processBuilder.redirectErrorStream(true);

			Process process = processBuilder.start();

			InputStream inputStream = process.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder outputBuilder = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println("path: " + line);
				outputBuilder.append(line).append(System.lineSeparator());
			}

			int exitCode = process.waitFor();

			if (exitCode == 0) {
				return outputBuilder.toString().trim();
			} else {
				System.out.println("PowerShell command failed to get server.xml path.");
				return null;
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}


	
	public static void modifyServerXml(String serverXmlPath, int newPort) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new File(serverXmlPath));

			Node connectorNode = findConnectorNode(doc);
			if (connectorNode != null) {
				Element connectorElement = (Element) connectorNode;
				connectorElement.setAttribute("port", String.valueOf(newPort));
			} else {
				System.out.println("Connector element not found in server.xml.");
				return;
			}

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File(serverXmlPath));
			transformer.transform(source, result);

			System.out.println("Server.xml file modified successfully port changed to 8081.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static Node findConnectorNode(Document doc) {
		NodeList connectorNodes = doc.getElementsByTagName("Connector");
		for (int i = 0; i < connectorNodes.getLength(); i++) {
			Node node = connectorNodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				if (element.hasAttribute("port")) {
					return node;
				}
			}
		}
		return null;
	}

	private static String findTomcatBinPath() {
        File[] roots = File.listRoots();
        for (File root : roots) {
            String tomcatPath = searchForTomcat(root.getAbsolutePath());
            if (tomcatPath != null) {
                return tomcatPath;
            }
        }
        return null;
    }
	
	 private static String searchForTomcat(String directory) {
	        File[] tomcatDirs = new File(directory).listFiles((dir, name) -> name.startsWith("apache-tomcat-"));

	        if (tomcatDirs != null) {
	            for (File tomcatDir : tomcatDirs) {
	                if (tomcatDir.isDirectory()) {
	                    File binDir = new File(tomcatDir, "bin");
	                    if (binDir.exists() && binDir.isDirectory()) {
	                        return binDir.getAbsolutePath();
	                    }
	                }
	            }
	        }
	        return null;
	    }
	
	private static void startTomcat(String tomcatBinPath) {
		try {
            String startupScript;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                startupScript = "startup.bat";
            } else {
                startupScript = "./startup.sh";
            }

            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", startupScript);
            processBuilder.directory(new File(tomcatBinPath));
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String output;
            while ((output = reader.readLine()) != null) {
                System.out.println(output);
            }

            int exitCode = process.waitFor();
            // Check the exitCode for error handling if needed
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
	
	private static String findLinuxServerXmlPath(String tomcatDirName) {
	    try {
	        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", "find / -name 'server.xml' 2>/dev/null");
	        Process process = processBuilder.start();
	        InputStream inputStream = process.getInputStream();
	        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
	        String output;
	        while ((output = reader.readLine()) != null) {
	            if (output.contains(tomcatDirName)) {
	                return output;
	            }
	        }
	        int exitCode = process.waitFor();
	        // System.out.println("Process exited with code: " + exitCode);
	    } catch (IOException | InterruptedException e) {
	        e.printStackTrace();
	    }
	    return null;
    }
	
	public static void main(String[] args) {
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("win")) {
			System.out.println("The Operating System is: " + osName);

			List<Integer> portList = new ArrayList<>();

			String[] command = { "powershell", "-Command",
					"(Get-NetTCPConnection -OwningProcess (Get-Process \"java\" -ErrorAction SilentlyContinue).Id) | ForEach-Object { $_.LocalPort }" };

			try {
				ProcessBuilder processBuilder = new ProcessBuilder(command);
				processBuilder.redirectErrorStream(true);
				Process process = processBuilder.start();

				InputStream inputStream = process.getInputStream();
				InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

				String output;
				try {
					while ((output = bufferedReader.readLine()) != null) {
						int port = Integer.parseInt(output.trim());
						portList.add(port);
					}
				} catch (Exception e) {
					System.out.println("Not Found Any Running Apache Tomcat Instance");
				}

				int exitCode = process.waitFor();
//			System.out.println("Exit Code: " + exitCode);

				bufferedReader.close();
				inputStreamReader.close();
				inputStream.close();
			} catch (IOException | InterruptedException | NumberFormatException e) {
				e.printStackTrace();
			}

			try {
				String powerShellCmd = "powershell.exe -ExecutionPolicy Bypass -Command " + "\"$serverXmlPaths = @(); "
						+ "$drives = Get-WmiObject Win32_LogicalDisk | Where-Object { $_.DriveType -eq 3 } | ForEach-Object { $_.DeviceID }; "
						+ "foreach ($drive in $drives) { "
						+ "   $tomcatDirs = Get-ChildItem -Path \\\"$drive\\\\*\\\" -Filter \\\"apache-tomcat-*\\\" -Directory -ErrorAction SilentlyContinue; "
						+ "   if ($tomcatDirs) { " + "       foreach ($tomcatDir in $tomcatDirs) { "
						+ "           $serverXmlPath = Get-ChildItem -Path $tomcatDir.FullName -Filter \\\"server.xml\\\" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName; "
						+ "           if ($serverXmlPath) { " + "               $serverXmlPaths += $serverXmlPath; "
						+ "           } " + "       } " + "   } " + "} " + "$serverXmlPaths\"";

				ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", powerShellCmd);
				processBuilder.redirectErrorStream(true);

				Process process = processBuilder.start();

				InputStream inputStream = process.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
				String output;
				while ((output = reader.readLine()) != null) {
					System.out.println("Server.xml File Path: " + output);
				}

				int exitCode = process.waitFor();
//			System.out.println("PowerShell process exited with code: " + exitCode);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}

			int[] portsArray = portList.stream().mapToInt(Integer::intValue).toArray();

			int tomcatPort = 0;
			if (portsArray.length > 0) {
				for (int port : portsArray) {
					tomcatPort = findTomcatPort(port);

					try {
						Runtime runTime = Runtime.getRuntime();
						Process proc = runTime.exec("cmd /c netstat -ano | findstr " + tomcatPort);

						BufferedReader bufferedReader = new BufferedReader(
								new InputStreamReader(proc.getInputStream()));
						String line = null;

						try {
							if ((line = bufferedReader.readLine()) != null) {
								int processIdString = line.lastIndexOf(" ");
//						        String processId = line.substring(processIdString+1);
								String processId = line.substring(processIdString, line.length());
								System.out.println(" process Id to Kill : " + processId);
								runTime.exec("cmd /c Taskkill /PID" + processId + " /T /F");
								System.out.println("process Killed: " + processId);
							}
						} catch (Exception e) {
							System.out.println("No process were found");
						}
					} catch (Exception e) {
						System.out.println("Error Occured");
					}
				} 	
//				String> tomcatDirs = findTomcatDirectories();
				String serverXmlPath = findXmlTomcatDirectories();
				
				if (serverXmlPath == null) {
					System.out.println("No Tomcat server.xml file found.");
					return;
				}

				// Step 2: Modify the "server.xml" file
				modifyServerXml(serverXmlPath, 8081);
				
				String tomcatBinPath = findTomcatBinPath();
		        if (tomcatBinPath == null) {
		            System.out.println("Tomcat installation directory not found.");
		            return;
		        }

		        // Use the dynamically obtained Tomcat path
		        System.out.println("Tomcat Bin Path: " + tomcatBinPath);

	            // Call the method to start Tomcat
	            startTomcat(tomcatBinPath);
			}

		} 
		else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
			System.out.println("The Operating System is: " + osName);
			//

			List<Integer> portList = new ArrayList<>();

			String[] command = { "bash", "-c", "lsof -i -n -P | grep 'java' | awk '{print $9}' | awk -F ':' '{print $2}' | sort -u" };

			try {
				ProcessBuilder processBuilder = new ProcessBuilder(command);
				processBuilder.redirectErrorStream(true);
				Process process = processBuilder.start();

				InputStream inputStream = process.getInputStream();
				InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

				String line;
				try {
					while ((line = bufferedReader.readLine()) != null) {
						int port = Integer.parseInt(line.trim());
						portList.add(port);
					}
				} catch (Exception e) {
					System.out.println("No ports were found");
				}

				int exitCode = process.waitFor();
				// System.out.println("Exit Code: " + exitCode);

				bufferedReader.close();
				inputStreamReader.close();
				inputStream.close();
			} catch (IOException | InterruptedException | NumberFormatException e) {
				e.printStackTrace();
			}

			try {
				ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c",
						"find / -name 'server.xml' 2>/dev/null");

				Process process = processBuilder.start();

				InputStream inputStream = process.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
				String output;
				while ((output = reader.readLine()) != null) {
					System.out.println("server.xml Path: " + output);
				}

				int exitCode = process.waitFor();
				// System.out.println("Process exited with code: " + exitCode);
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			int[] portsArray = portList.stream().mapToInt(Integer::intValue).toArray();

			int tomcatPort = 0;
			if (portsArray.length > 0) {
				for (int port : portsArray) {
					tomcatPort = findTomcatPort(port);

					try {
						ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c",
								"lsof -i :" + tomcatPort + " | grep 'java' | awk '{print $2}'");
						Process process = processBuilder.start();

						InputStream inputStream = process.getInputStream();
						BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
						String output;
						while ((output = reader.readLine()) != null) {
							int processId = Integer.parseInt(output.trim());
							killProcess(processId);
						}

						int exitCode = process.waitFor();
						// System.out.println("Process exited with code: " + exitCode);
					} catch (IOException | InterruptedException | NumberFormatException e) {
						e.printStackTrace();
					}
				}
				String tomcatDirName = "tomcat8.5.87"; // Replace with the specific Tomcat directory name
				String serverXmlPath = findLinuxServerXmlPath(tomcatDirName);
		        // Change the port to 8081 in the server.xml file
		        if (serverXmlPath != null) {
		            try {
		                File serverXmlFile = new File(serverXmlPath);
		                StringBuilder xmlContent = new StringBuilder();
		                BufferedReader reader = new BufferedReader(new FileReader(serverXmlFile));
		                String line;
		                while ((line = reader.readLine()) != null) {
		                    xmlContent.append(line).append(System.lineSeparator());
		                }
		                reader.close();

		                // Locate the port configuration in the XML content
		                String originalPortPattern = "port=\"(\\d+)\"";
		                String modifiedXmlContent = xmlContent.toString().replaceAll(originalPortPattern, "port=\"8081\"");

		                // Save the modified XML content back to the server.xml file
		                BufferedWriter writer = new BufferedWriter(new FileWriter(serverXmlFile));
		                writer.write(modifiedXmlContent);
		                writer.close();

		                System.out.println("Tomcat port changed to 8081 in server.xml: " + serverXmlPath);
		            } catch (IOException e) {
		                e.printStackTrace();
		            }
		        } else {
		            System.out.println("server.xml file not found.");
		        }
				
				String tomcatBinPath = findTomcatBinPath();
		        if (tomcatBinPath == null) {
		            System.out.println("Tomcat installation directory not found.");
		            return;
		        }

		        System.out.println("Tomcat Bin Path: " + tomcatBinPath);

	            startTomcat(tomcatBinPath);

			}

			//
		}
		
		else {
			System.out.println("Non-Supported Windows");
		}
	}

}
