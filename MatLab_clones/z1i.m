function M = z1i(u,v,w)
% z1i(u,v,w): Mobius: {u, v, w} -> {0, 1, infty}
%   Greate normalized Mobius mapping u, v, w to 0, 1, infty.
M=[1.0,-u;1.0,-w];
coeff=(v-w)/(v-u);
M(1,1)=coeff*M(1,1);
M(1,2)=coeff*M(1,2);
M=M/(det(M));
end