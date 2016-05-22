package com.jsankey.overseer;

import java.io.IOException;

public class OverseerApplication {

  public static void main(String[] args) {
    System.out.print("Hello world\n");
	
    try {
      Configuration.printHelpOn(System.out);
    } catch (IOException e) {
      System.err.print("Exception printing help");
	}
  }
}
