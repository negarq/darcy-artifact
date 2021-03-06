package com.timberglund.poetry.content;

import java.util.List;
import java.util.Iterator;
import java.util.Arrays;
import java.util.stream.Stream;

public class Ascetic 
  extends Poem {
  
  public String getAuthor() {
    return "G.K. Chesterton";
  }
  
  public String getTitle() {
    return "The Song of the Strange Ascetic";
  }

  public Stream<String> getLines() {
    String[] lines = {
      "If I had been a Heathen,",
      "I'd have praised the purple vine,",
      "My slaves should dig the vineyards,",
      "And I would drink the wine.",
      "But Higgins is a Heathen,",
      "And his slaves grow lean and grey,",
      "That he may drink some tepid milk",
      "Exactly twice a day.",
      "",
      "If I had been a Heathen,",
      "I'd have crowned Neaera's curls,",
      "And filled my life with love affairs,",
      "My house with dancing girls;",
      "But Higgins is a Heathen,",
      "And to lecture rooms is forced,",
      "Where his aunts, who are not married,",
      "Demand to be divorced.",
      "",
      "If I had been a Heathen,",
      "I'd have sent my armies forth,",
      "And dragged behind my chariots",
      "The Chieftains of the North.",
      "But Higgins is a Heathen,",
      "And he drives the dreary quill,",
      "To lend the poor that funny cash",
      "That makes them poorer still.",
      "",
      "If I had been a Heathen,",
      "I'd have piled my pyre on high,",
      "And in a great red whirlwind",
      "Gone roaring to the sky;",
      "But Higgins is a Heathen,",
      "And a richer man than I:",
      "And they put him in an oven,",
      "Just as if he were a pie.",
      "",
      "Now who that runs can read it,",
      "The riddle that I write,",
      "Of why this poor old sinner,",
      "Should sin without delight-",
      "But I, I cannot read it",
      "(Although I run and run),",
      "Of them that do not have the faith,",
      "And will not have the fun."
    };
    
    return Arrays.asList(lines).stream();
  }
}
