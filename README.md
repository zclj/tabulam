# tabulam

Clojure library to access boardgamegeek API

BoardGameGeek XML API Terms of Use: http://boardgamegeek.com/xmlapi/termsofuse

See http://www.boardgamegeek.com/xmlapi2/thing?id=31260&page=25&historical=1 for an example of the raw XML data received from the API.

## Usage

To get an xml-zipper of a specific game and page call `get-game-page` with the thing-id (from boardgamegeek) and the page to retreive.

``` clj
(get-game-page 31260 23)
```

Use `game->map` to produce a map of the information in the zipper. `game->map` work with a vector of pages and merges the ratings for all those pages.

``` clj
(game->map [(get-game-page 31260 23)])
```

To get all pages of historical data for a game use `get-game-history`. This will give you a map of information from the given starting page number to the last available page of information. Currently there is a 5s delay between page calls to not hammer on the API to hard.

``` clj
(get-game-history 31260 23)
```

## License

Copyright © 2014 Stefan Karlsson

Distributed under the Eclipse Public License
