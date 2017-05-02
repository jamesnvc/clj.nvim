# Clojure Refactoring in Neovim

Installation using [`plug.vim`](https://github.com/junegunn/vim-plug):

```
Plug 'jamesnvc/clj.nvim', {'do': 'lein uberjar'}
```

Commands:

 - `:TidyNS` will sort the requires in the current Clojure file according to [these rules](https://stuartsierra.com/2016/clojure-how-to-ns.html)
 - `:CleanRefers` will remove any refer'd vars in the require form that aren't actually used in the body
