# tabulam

Clojure library to access boardgamegeek API

BoardGameGeek XML API Terms of Use: http://boardgamegeek.com/xmlapi/termsofuse

## Changes in 0.2

Since BGG no longer provide their history API 0.2 will be a re-write to reflect that change.

## Usage (depricated as of 0.2 note above)

To get an xml-zipper of a specific game and page call `get-game-page` with the thing-id (from boardgamegeek) and the page to retrieve.

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

Copyright © 2014-2017 Stefan Karlsson

Distributed under the Eclipse Public License
