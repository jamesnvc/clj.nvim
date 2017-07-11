# Clojure Refactoring in Neovim

Installation using [`plug.vim`](https://github.com/junegunn/vim-plug):

```
Plug 'jamesnvc/clj.nvim', {'do': 'lein uberjar'}
```

Commands:

 - `:TidyNS` will sort the requires in the current Clojure file according to [these rules](https://stuartsierra.com/2016/clojure-how-to-ns.html)
 - `:CleanRefers` will remove any refer'd vars in the require form that aren't actually used in the body

You'll probably want to have the `TidyNS` run on save.
I have the following in my `init.vm`

```vim
augroup cleanUp  "{{
  autocmd!
  autocmd BufWritePre *.clj,*.cljs :TidyNS
augroup END  " }}
```

Note that because this plugin runs asynchronously, it will probably end up changing the file after you save it, so you'll need to save twice/until it reaches a steady-state.
